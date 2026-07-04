package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BorderService {

    private static final double BORDER_SIZE_EPSILON = 0.000001D;
    private static final double BORDER_SAFE_MARGIN_BLOCKS = 0.3D;

    private enum RoundState {
        LOBBY,
        COUNTDOWN,
        ACTIVE
    }

    private final Plugin plugin;
    private final WorldBorderApi worldBorderApi;
    private final PlayerBorderRepository playerBorderRepository;
    private final LevelBorderSettings settings;
    private final BorderSizeCalculator sizeCalculator;
    private final BorderNotifier notifier;
    private final Messages messages;
    private final LuckPermsRoleService luckPermsRoleService;
    private final Map<UUID, BukkitTask> borderAnimationTasks = new HashMap<>();
    private final Map<UUID, Double> animatedBorderSizes = new HashMap<>();
    private final Map<UUID, Integer> roundKills = new HashMap<>();
    private final Map<UUID, Integer> roundDeaths = new HashMap<>();
    private final Set<UUID> roundPlayers = new HashSet<>();
    private final Set<UUID> spectatorPlayers = new HashSet<>();
    private final Set<UUID> manuallyStoppedPlayers = new HashSet<>();

    private RoundState roundState = RoundState.LOBBY;
    private BukkitTask startCountdownTask;
    private BukkitTask roundEndTask;

    public BorderService(
            Plugin plugin,
            WorldBorderApi worldBorderApi,
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            BorderNotifier notifier,
            Messages messages,
            LuckPermsRoleService luckPermsRoleService
    ) {
        this.plugin = plugin;
        this.worldBorderApi = worldBorderApi;
        this.playerBorderRepository = playerBorderRepository;
        this.settings = settings;
        this.sizeCalculator = sizeCalculator;
        this.notifier = notifier;
        this.messages = messages;
        this.luckPermsRoleService = luckPermsRoleService;
    }

    public void applyNextTick(Player player, BorderNotification notification) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyCurrentState(player, notification));
    }

    public void applyLater(Player player, BorderNotification notification, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyCurrentState(player, notification), delayTicks);
    }

    public void reapplyOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyNextTick(player, BorderNotification.NONE);
        }
    }

    public void refreshRuntimeSettings() {
        reapplyOnlinePlayers();
        if (roundState != RoundState.ACTIVE) {
            return;
        }

        cancelRoundEndTask();
        scheduleRoundEnd();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (roundState != RoundState.ACTIVE || !isActive(player)) {
                continue;
            }

            PlayerScore score = score(player);
            checkTargetLevel(player, getOrCreateBorderData(player), Math.max(0, player.getLevel()));
            checkTargetBorder(player, score.borderSize());
        }

        checkEliminationWinner();
    }

    public boolean isSpectator(Player player) {
        return spectatorPlayers.contains(player.getUniqueId());
    }

    public void handleLevelChange(Player player, int newLevel) {
        if (!isActive(player)) {
            return;
        }
        ensureRoundPlayer(player);

        PlayerBorderData data = getOrCreateBorderData(player);
        int normalizedLevel = Math.max(0, newLevel);
        boolean reachedNewHighestLevel = normalizedLevel > data.maxReachedLevel();

        if (reachedNewHighestLevel) {
            data = data.withMaxReachedLevel(normalizedLevel);
            playerBorderRepository.save(data);
        }

        if (settings.usesCurrentLevelMode()) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_CHANGED);
        } else if (reachedNewHighestLevel) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_UP);
        }

        checkTargetLevel(player, data, normalizedLevel);
    }

    public void handlePlayerKill(Player killer, Player killed) {
        if (!isActive(killer)) {
            return;
        }
        if (killer.getUniqueId().equals(killed.getUniqueId())) {
            return;
        }
        if (!isActive(killed)) {
            return;
        }

        roundKills.merge(killer.getUniqueId(), 1, Integer::sum);

        if (settings.usesCurrentLevelMode() || !settings.highestKillBonusEnabled()) {
            return;
        }

        ensureRoundPlayer(killer);
        ensureRoundPlayer(killed);

        PlayerBorderData killedData = updateMaxReachedLevel(killed, getOrCreateBorderData(killed));
        int bonusLevels = Math.max(0, killedData.maxReachedLevel());
        if (settings.highestKillBonusInheritsVictimBonus()) {
            bonusLevels = addLevels(bonusLevels, killedData.killBonusLevels());
        }
        if (bonusLevels <= 0) {
            return;
        }

        PlayerBorderData killerData = updateMaxReachedLevel(killer, getOrCreateBorderData(killer));
        int newKillBonusLevels = addLevels(killerData.killBonusLevels(), bonusLevels);
        if (newKillBonusLevels == killerData.killBonusLevels()) {
            return;
        }

        killerData = killerData.withKillBonusLevels(newKillBonusLevels);
        playerBorderRepository.save(killerData);
        apply(killer, killerData, Math.max(0, killer.getLevel()), BorderNotification.KILL_BONUS);
    }

    public void handlePlayerDeath(Player player) {
        if (!isActive(player)) {
            return;
        }

        roundDeaths.merge(player.getUniqueId(), 1, Integer::sum);
        if (!settings.spectatorModeEnabled() && settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        enterSpectator(player, BorderNotification.SPECTATOR);
        checkEliminationWinner();
    }

    public void start(int countdownSeconds) {
        cancelStartCountdown();
        cancelRoundEndTask();

        int boundedCountdownSeconds = Math.max(0, Math.min(countdownSeconds, settings.maxStartCountdownSeconds()));
        roundState = RoundState.COUNTDOWN;
        roundPlayers.clear();
        spectatorPlayers.clear();
        manuallyStoppedPlayers.clear();
        roundKills.clear();
        roundDeaths.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cancelBorderAnimation(player);
            luckPermsRoleService.clear(player);
            worldBorderApi.resetWorldBorderToGlobal(player);
        }

        if (boundedCountdownSeconds <= 0) {
            activateRound();
            return;
        }

        BukkitRunnable countdown = new BukkitRunnable() {
            private int remainingSeconds = boundedCountdownSeconds;

            @Override
            public void run() {
                if (roundState != RoundState.COUNTDOWN) {
                    startCountdownTask = null;
                    cancel();
                    return;
                }

                if (remainingSeconds <= 0) {
                    startCountdownTask = null;
                    activateRound();
                    cancel();
                    return;
                }

                broadcastCountdown(remainingSeconds);
                remainingSeconds--;
            }
        };

        startCountdownTask = countdown.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop(Player player) {
        UUID playerId = player.getUniqueId();
        cancelBorderAnimation(player);
        manuallyStoppedPlayers.add(playerId);
        spectatorPlayers.remove(playerId);
        luckPermsRoleService.clear(player);
        worldBorderApi.resetWorldBorderToGlobal(player);
    }

    public void reset(Player player) {
        cancelBorderAnimation(player);
        UUID playerId = player.getUniqueId();
        manuallyStoppedPlayers.remove(playerId);
        spectatorPlayers.remove(playerId);

        PlayerBorderData data = createInitialBorderData(player, Math.max(0, player.getLevel()));
        playerBorderRepository.save(data);

        if (roundState == RoundState.ACTIVE) {
            roundPlayers.add(player.getUniqueId());
            luckPermsRoleService.markActive(player);
            apply(player, data, Math.max(0, player.getLevel()), BorderNotification.JOIN);
        } else if (roundState == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            applyLobbyBorder(player);
        } else {
            worldBorderApi.resetWorldBorderToGlobal(player);
        }
    }

    public void shutdown() {
        cancelStartCountdown();
        cancelRoundEndTask();

        for (BukkitTask task : borderAnimationTasks.values()) {
            task.cancel();
        }
        borderAnimationTasks.clear();
        animatedBorderSizes.clear();
    }

    private void applyCurrentState(Player player, BorderNotification notification) {
        if (roundState == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            applyLobbyBorder(player);
            return;
        }

        if (roundState == RoundState.COUNTDOWN) {
            cancelBorderAnimation(player);
            luckPermsRoleService.clear(player);
            worldBorderApi.resetWorldBorderToGlobal(player);
            return;
        }

        if (spectatorPlayers.contains(player.getUniqueId())) {
            applySpectator(player, notification);
            return;
        }

        if (manuallyStoppedPlayers.contains(player.getUniqueId())) {
            worldBorderApi.resetWorldBorderToGlobal(player);
            return;
        }

        if (!roundPlayers.contains(player.getUniqueId())) {
            if (usesSpectatorForLateJoiners()) {
                enterSpectator(player, notification);
                return;
            }
            activatePlayerFromCurrentPosition(player, notification);
            return;
        }

        luckPermsRoleService.markActive(player);
        apply(player, notification);
    }

    private void ensureRoundPlayer(Player player) {
        if (roundState == RoundState.ACTIVE
                && !manuallyStoppedPlayers.contains(player.getUniqueId())
                && !spectatorPlayers.contains(player.getUniqueId())
                && !roundPlayers.contains(player.getUniqueId())) {
            if (usesSpectatorForLateJoiners()) {
                enterSpectator(player, BorderNotification.NONE);
                return;
            }
            activatePlayerFromCurrentPosition(player, BorderNotification.NONE);
        }
    }

    private boolean usesSpectatorForLateJoiners() {
        return settings.spectatorModeEnabled() || settings.endCondition() == RoundEndCondition.ELIMINATION;
    }

    private void apply(Player player, BorderNotification notification) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = updateMaxReachedLevel(player, getOrCreateBorderData(player));

        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    private void apply(Player player, PlayerBorderData data, int currentLevel, BorderNotification notification) {
        if (!player.isOnline()) {
            return;
        }

        double size = sizeCalculator.calculate(resolveLevelForBorder(data, currentLevel));
        double previousSize = currentDisplayedBorderSize(player, data);
        Location center = data.toLocation(player.getWorld());
        applyBorder(player, center, previousSize, size, notification);

        playerBorderRepository.save(data.withLastAppliedBorderSize(size));

        notifyPlayer(player, notification, size, previousSize);
        checkTargetBorder(player, size);
    }

    private void applyBorder(
            Player player,
            Location center,
            double previousSize,
            double size,
            BorderNotification notification
    ) {
        if (!shouldAnimateSizeChange(notification, previousSize, size)) {
            cancelBorderAnimation(player);
            keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
            worldBorderApi.setBorder(player, size, center);
            return;
        }

        keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
        animateBorder(player, center, previousSize, size);
    }

    private void applyLobbyBorder(Player player) {
        cancelBorderAnimation(player);

        double lobbyRadius = settings.lobbyRadiusBlocks();
        if (lobbyRadius <= 0.0D) {
            worldBorderApi.resetWorldBorderToGlobal(player);
            return;
        }

        worldBorderApi.setBorder(player, lobbyRadius * 2.0D, player.getWorld().getSpawnLocation());
    }

    private void applySpectator(Player player, BorderNotification notification) {
        cancelBorderAnimation(player);
        worldBorderApi.resetWorldBorderToGlobal(player);
        luckPermsRoleService.markSpectator(player);

        if (notification == BorderNotification.SPECTATOR
                || notification == BorderNotification.JOIN
                || notification == BorderNotification.RESPAWN) {
            notifier.showSpectator(player);
        }
    }

    private void enterSpectator(Player player, BorderNotification notification) {
        spectatorPlayers.add(player.getUniqueId());
        manuallyStoppedPlayers.remove(player.getUniqueId());
        applySpectator(player, notification);
    }

    private void broadcastCountdown(int remainingSeconds) {
        String message = messages.text(
                "service.countdown",
                Messages.placeholder("seconds", remainingSeconds)
        );
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void activateRound() {
        roundState = RoundState.ACTIVE;
        roundPlayers.clear();
        spectatorPlayers.clear();
        manuallyStoppedPlayers.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (roundState != RoundState.ACTIVE) {
                break;
            }
            activatePlayerFromCurrentPosition(player, BorderNotification.JOIN);
        }

        if (roundState == RoundState.ACTIVE) {
            scheduleRoundEnd();
            checkEliminationWinner();
        }
    }

    private void activatePlayerFromCurrentPosition(Player player, BorderNotification notification) {
        roundPlayers.add(player.getUniqueId());
        spectatorPlayers.remove(player.getUniqueId());
        manuallyStoppedPlayers.remove(player.getUniqueId());
        luckPermsRoleService.markActive(player);
        PlayerBorderData data = createInitialBorderData(player, Math.max(0, player.getLevel()));
        playerBorderRepository.save(data);
        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    private void scheduleRoundEnd() {
        cancelRoundEndTask();
        if (settings.endCondition() != RoundEndCondition.TIMED_SCORE) {
            return;
        }

        long delayTicks = settings.roundDurationMinutes() * 60L * 20L;
        roundEndTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::finishTimedScoreRound, delayTicks);
    }

    private void finishTimedScoreRound() {
        if (roundState != RoundState.ACTIVE) {
            return;
        }

        PlayerScore winner = findScoreWinner();
        if (winner == null) {
            finishRoundWithoutWinner("service.end-reason-time");
            return;
        }

        finishRoundWithWinner(winner.player(), "service.end-reason-time");
    }

    private PlayerScore findScoreWinner() {
        PlayerScore best = null;
        boolean tied = false;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isActive(player)) {
                continue;
            }

            PlayerScore candidate = score(player);
            if (best == null) {
                best = candidate;
                tied = false;
                continue;
            }

            int comparison = compareScores(candidate, best);
            if (comparison > 0) {
                best = candidate;
                tied = false;
            } else if (comparison == 0) {
                tied = true;
            }
        }

        return tied ? null : best;
    }

    private PlayerScore score(Player player) {
        PlayerBorderData data = updateMaxReachedLevel(player, getOrCreateBorderData(player));
        int currentLevel = Math.max(0, player.getLevel());
        int borderLevel = resolveLevelForBorder(data, currentLevel);
        return new PlayerScore(
                player,
                sizeCalculator.calculate(borderLevel),
                Math.max(data.maxReachedLevel(), currentLevel),
                roundKills.getOrDefault(player.getUniqueId(), 0),
                roundDeaths.getOrDefault(player.getUniqueId(), 0)
        );
    }

    private int compareScores(PlayerScore first, PlayerScore second) {
        int borderComparison = Double.compare(first.borderSize(), second.borderSize());
        if (borderComparison != 0) {
            return borderComparison;
        }

        for (String tiebreaker : settings.scoreTiebreakers()) {
            int comparison = compareScoreTiebreaker(first, second, tiebreaker);
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private int compareScoreTiebreaker(PlayerScore first, PlayerScore second, String tiebreaker) {
        if (tiebreaker == null) {
            return 0;
        }

        return switch (tiebreaker.trim().toLowerCase()) {
            case "kills" -> Integer.compare(first.kills(), second.kills());
            case "highest-level" -> Integer.compare(first.highestLevel(), second.highestLevel());
            case "deaths-ascending" -> Integer.compare(second.deaths(), first.deaths());
            default -> 0;
        };
    }

    private void checkTargetLevel(Player player, PlayerBorderData data, int currentLevel) {
        if (roundState != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.TARGET_LEVEL) {
            return;
        }

        int reachedLevel = settings.usesCurrentLevelMode() ? currentLevel : Math.max(data.maxReachedLevel(), currentLevel);
        if (reachedLevel >= settings.winTargetLevel()) {
            finishRoundWithWinner(player, "service.end-reason-target-level");
        }
    }

    private void checkTargetBorder(Player player, double borderSize) {
        if (roundState != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.TARGET_BORDER) {
            return;
        }

        if (borderSize >= settings.winTargetBorderSizeBlocks()) {
            finishRoundWithWinner(player, "service.end-reason-target-border");
        }
    }

    private void checkEliminationWinner() {
        if (roundState != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        Player remaining = null;
        int activePlayers = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isActive(player)) {
                continue;
            }
            remaining = player;
            activePlayers++;
        }

        if (activePlayers == 1) {
            finishRoundWithWinner(remaining, "service.end-reason-elimination");
        } else if (activePlayers == 0) {
            finishRoundWithoutWinner("service.end-reason-elimination");
        }
    }

    private void finishRoundWithWinner(Player winner, String reasonKey) {
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-winner",
                Messages.placeholder("winner", winner.getName()),
                Messages.placeholder("reason", reason)
        ));
        finishRound();
    }

    private void finishRoundWithoutWinner(String reasonKey) {
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-no-winner",
                Messages.placeholder("reason", reason)
        ));
        finishRound();
    }

    private void finishRound() {
        cancelStartCountdown();
        cancelRoundEndTask();
        roundState = RoundState.LOBBY;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cancelBorderAnimation(player);
            luckPermsRoleService.clear(player);
            applyLobbyBorder(player);
        }

        roundPlayers.clear();
        spectatorPlayers.clear();
        manuallyStoppedPlayers.clear();
        roundKills.clear();
        roundDeaths.clear();
    }

    private record PlayerScore(Player player, double borderSize, int highestLevel, int kills, int deaths) {
    }

    private double currentDisplayedBorderSize(Player player, PlayerBorderData data) {
        return animatedBorderSizes.getOrDefault(player.getUniqueId(), data.lastAppliedBorderSize());
    }

    private void animateBorder(Player player, Location center, double previousSize, double size) {
        cancelBorderAnimation(player);

        UUID playerId = player.getUniqueId();
        int totalTicks = Math.max(1, (int) Math.ceil(settings.borderTransitionMilliseconds() / 50.0D));
        animatedBorderSizes.put(playerId, previousSize);
        worldBorderApi.setBorder(player, previousSize, center);

        BukkitRunnable animation = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    borderAnimationTasks.remove(playerId);
                    animatedBorderSizes.remove(playerId);
                    cancel();
                    return;
                }

                elapsedTicks++;
                double progress = Math.min(1.0D, elapsedTicks / (double) totalTicks);
                double easedProgress = smoothStep(progress);
                double currentSize = previousSize + ((size - previousSize) * easedProgress);

                animatedBorderSizes.put(playerId, currentSize);
                worldBorderApi.setBorder(player, currentSize, center);

                if (progress >= 1.0D) {
                    borderAnimationTasks.remove(playerId);
                    animatedBorderSizes.remove(playerId);
                    worldBorderApi.setBorder(player, size, center);
                    cancel();
                }
            }
        };

        borderAnimationTasks.put(playerId, animation.runTaskTimer(plugin, 1L, 1L));
    }

    private double smoothStep(double progress) {
        return progress * progress * (3.0D - (2.0D * progress));
    }

    private void cancelBorderAnimation(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = borderAnimationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        animatedBorderSizes.remove(playerId);
    }

    private void cancelStartCountdown() {
        if (startCountdownTask != null) {
            startCountdownTask.cancel();
            startCountdownTask = null;
        }
    }

    private void cancelRoundEndTask() {
        if (roundEndTask != null) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
    }

    private boolean isActive(Player player) {
        UUID playerId = player.getUniqueId();
        return roundState == RoundState.ACTIVE
                && roundPlayers.contains(playerId)
                && !spectatorPlayers.contains(playerId)
                && !manuallyStoppedPlayers.contains(playerId);
    }

    private void keepPlayerInsideShrinkingBorder(Player player, Location center, double previousSize, double size) {
        if (size >= previousSize - BORDER_SIZE_EPSILON) {
            return;
        }

        Location playerLocation = player.getLocation();
        double maxOffset = safeBorderOffset(size);
        double clampedX = clamp(playerLocation.getX(), center.getX() - maxOffset, center.getX() + maxOffset);
        double clampedZ = clamp(playerLocation.getZ(), center.getZ() - maxOffset, center.getZ() + maxOffset);

        if (Math.abs(clampedX - playerLocation.getX()) <= BORDER_SIZE_EPSILON
                && Math.abs(clampedZ - playerLocation.getZ()) <= BORDER_SIZE_EPSILON) {
            return;
        }

        playerLocation.setX(clampedX);
        playerLocation.setZ(clampedZ);
        player.teleport(playerLocation);
    }

    private double safeBorderOffset(double size) {
        double halfSize = size / 2.0D;
        double safeMargin = Math.min(BORDER_SAFE_MARGIN_BLOCKS, Math.max(0.0D, halfSize - 0.05D));
        return Math.max(0.0D, halfSize - safeMargin);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean shouldAnimateSizeChange(BorderNotification notification, double previousSize, double size) {
        if (settings.borderTransitionMilliseconds() <= 0L || previousSize <= 0.0D) {
            return false;
        }
        if (Math.abs(size - previousSize) <= BORDER_SIZE_EPSILON) {
            return false;
        }
        return notification == BorderNotification.LEVEL_UP
                || notification == BorderNotification.LEVEL_CHANGED
                || notification == BorderNotification.KILL_BONUS;
    }

    private void notifyPlayer(Player player, BorderNotification notification, double size, double previousSize) {
        if (notification == BorderNotification.LEVEL_UP && size > previousSize + BORDER_SIZE_EPSILON) {
            notifier.showLevelUp(player, size);
        } else if (notification == BorderNotification.LEVEL_CHANGED) {
            if (size > previousSize + BORDER_SIZE_EPSILON) {
                notifier.showLevelUp(player, size);
            } else if (size < previousSize - BORDER_SIZE_EPSILON) {
                notifier.showBorderChanged(player, size);
            }
        } else if (notification == BorderNotification.KILL_BONUS && size > previousSize + BORDER_SIZE_EPSILON) {
            notifier.showKillBonus(player, size);
        } else if (notification == BorderNotification.JOIN) {
            notifier.showJoined(player, size);
        } else if (notification == BorderNotification.RESPAWN) {
            notifier.showRespawned(player, size);
        }
    }

    private int resolveLevelForBorder(PlayerBorderData data, int currentLevel) {
        if (settings.usesCurrentLevelMode()) {
            return Math.max(0, currentLevel);
        }
        return addLevels(data.maxReachedLevel(), data.killBonusLevels());
    }

    private PlayerBorderData updateMaxReachedLevel(Player player, PlayerBorderData data) {
        int currentLevel = Math.max(0, player.getLevel());
        if (currentLevel <= data.maxReachedLevel()) {
            return data;
        }

        PlayerBorderData updated = data.withMaxReachedLevel(currentLevel);
        playerBorderRepository.save(updated);
        return updated;
    }

    private int addLevels(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private PlayerBorderData getOrCreateBorderData(Player player) {
        int currentLevel = Math.max(0, player.getLevel());
        PlayerBorderData existing = playerBorderRepository.find(
                player,
                settings.usesCurrentLevelMode(),
                sizeCalculator::calculate
        );
        if (existing != null) {
            return existing;
        }

        PlayerBorderData created = createInitialBorderData(player, currentLevel);
        playerBorderRepository.save(created);
        return created;
    }

    private PlayerBorderData createInitialBorderData(Player player, int currentLevel) {
        Location location = player.getLocation();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        if (settings.centerAtBlockCenter()) {
            x = location.getBlockX() + 0.5D;
            y = location.getBlockY() + 0.5D;
            z = location.getBlockZ() + 0.5D;
        }

        return new PlayerBorderData(
                player.getUniqueId(),
                location.getWorld().getUID(),
                location.getWorld().getName(),
                x,
                y,
                z,
                currentLevel,
                0,
                sizeCalculator.calculate(currentLevel)
        );
    }
}
