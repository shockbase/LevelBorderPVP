package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.DimensionPolicy;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class BorderService {

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final LuckPermsRoleService luckPermsRoleService;
    private final BorderRenderer borderRenderer;
    private final PlayerBorderDataService playerBorderDataService;
    private final BorderSizeCalculator sizeCalculator;
    private final BorderNotifier notifier;
    private final RoundPlayerTracker roundPlayers = new RoundPlayerTracker();
    private final Set<UUID> startCandidateIds = new HashSet<>();
    private final RoundScoreTracker roundScores;

    private RoundState roundState = RoundState.IDLE;
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
        this.settings = settings;
        this.messages = messages;
        this.luckPermsRoleService = luckPermsRoleService;
        this.sizeCalculator = sizeCalculator;
        this.notifier = notifier;
        this.borderRenderer = new BorderRenderer(worldBorderApi, playerBorderRepository, settings, sizeCalculator, notifier);
        this.playerBorderDataService = new PlayerBorderDataService(playerBorderRepository, settings, sizeCalculator);
        this.roundScores = new RoundScoreTracker(settings, sizeCalculator, playerBorderDataService, roundPlayers);
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
            if (!isActive(player)) {
                continue;
            }

            int currentLevel = Math.max(0, player.getLevel());
            PlayerScore score = roundScores.score(player);
            checkTargetLevel(player, settings.usesCurrentLevelMode() ? currentLevel : score.highestLevel());
            checkTargetBorder(player, score.borderSize());
        }

        checkEliminationWinner();
    }

    public boolean isSpectator(Player player) {
        return roundPlayers.isSpectator(player);
    }

    public boolean isActiveRoundPlayer(Player player) {
        return isActive(player);
    }

    public boolean shouldApplyPortalRules(Player player) {
        return settings.dimensionPolicy().usesSafePveDimensionRules() && isActiveRoundPlayer(player);
    }

    public boolean shouldApplyDimensionPvpRules(Player player) {
        return settings.dimensionPolicy().usesSafePveDimensionRules() && isActiveRoundPlayer(player);
    }

    public void handleLevelChange(Player player, int newLevel) {
        if (!isActive(player)) {
            return;
        }
        ensureRoundPlayer(player);

        PlayerBorderData data = playerBorderDataService.getOrCreate(player);
        int normalizedLevel = Math.max(0, newLevel);
        boolean reachedNewHighestLevel = normalizedLevel > data.maxReachedLevel();

        if (reachedNewHighestLevel) {
            data = data.withMaxReachedLevel(normalizedLevel);
            playerBorderDataService.save(data);
        }

        if (settings.usesCurrentLevelMode()) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_CHANGED);
        } else if (reachedNewHighestLevel) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_UP);
        }

        int reachedLevel = settings.usesCurrentLevelMode()
                ? normalizedLevel
                : Math.max(data.maxReachedLevel(), normalizedLevel);
        checkTargetLevel(player, reachedLevel);
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

        ensureRoundPlayer(killer);
        ensureRoundPlayer(killed);
        roundPlayers.recordKill(killer);

        double radiusGainedBlocks = applyPlayerKillBonus(killer, killed);
        notifier.showPlayerKill(killer, killed.getName(), radiusGainedBlocks);
    }

    public void handlePlayerDeath(Player player, Player killer) {
        if (!isActive(player)) {
            return;
        }

        roundPlayers.recordDeath(player);
        if (!settings.spectatorModeEnabled() && settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        boolean eliminatedByPlayer = isActivePlayerKill(killer, player);
        enterSpectator(player, eliminatedByPlayer ? BorderNotification.NONE : BorderNotification.SPECTATOR);
        if (eliminatedByPlayer) {
            notifier.showEliminated(player, killer.getName());
        }
        checkEliminationWinner();
    }

    private double applyPlayerKillBonus(Player killer, Player killed) {
        if (settings.usesCurrentLevelMode() || !settings.highestKillBonusEnabled()) {
            return 0.0D;
        }
        if (!roundPlayers.claimKillBonus(killed)) {
            return 0.0D;
        }

        double previousBorderSize = currentBorderSize(killer);
        PlayerBorderData killedData = playerBorderDataService.updateMaxReachedLevel(killed, playerBorderDataService.getOrCreate(killed));
        int bonusLevels = Math.max(0, killedData.maxReachedLevel());
        if (settings.highestKillBonusInheritsVictimBonus()) {
            bonusLevels = playerBorderDataService.addLevels(bonusLevels, killedData.killBonusLevels());
        }
        if (bonusLevels <= 0) {
            return 0.0D;
        }

        PlayerBorderData killerData = playerBorderDataService.updateMaxReachedLevel(killer, playerBorderDataService.getOrCreate(killer));
        int newKillBonusLevels = playerBorderDataService.addLevels(killerData.killBonusLevels(), bonusLevels);
        if (newKillBonusLevels == killerData.killBonusLevels()) {
            return 0.0D;
        }

        killerData = killerData.withKillBonusLevels(newKillBonusLevels);
        playerBorderDataService.save(killerData);

        int currentLevel = Math.max(0, killer.getLevel());
        double newBorderSize = borderSize(killerData, currentLevel);
        apply(killer, killerData, currentLevel, BorderNotification.PLAYER_KILL);
        return Math.max(0.0D, (newBorderSize - previousBorderSize) / 2.0D);
    }

    private double currentBorderSize(Player player) {
        return roundScores.score(player).borderSize();
    }

    private double borderSize(PlayerBorderData data, int currentLevel) {
        int borderLevel = playerBorderDataService.resolveLevelForBorder(data, currentLevel);
        return sizeCalculator.calculate(borderLevel);
    }

    private boolean isActivePlayerKill(Player killer, Player killed) {
        return killer != null
                && !killer.getUniqueId().equals(killed.getUniqueId())
                && isActive(killer);
    }

    public void start(int countdownSeconds) {
        cancelStartCountdown();
        cancelRoundEndTask();

        int boundedCountdownSeconds = Math.max(0, Math.min(countdownSeconds, settings.maxStartCountdownSeconds()));
        roundState = RoundState.COUNTDOWN;
        roundPlayers.clearRound();
        startCandidateIds.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            if (isInsideLobbyBorder(player)) {
                startCandidateIds.add(player.getUniqueId());
                borderRenderer.resetToGlobal(player);
            } else {
                enterSpectator(player, BorderNotification.SPECTATOR);
            }
        }

        if (boundedCountdownSeconds <= 0) {
            activateRound();
            return;
        }

        showSpreadOutToStartCandidates(boundedCountdownSeconds);

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

    public void lobby() {
        enterLobby(true);
    }

    public void stop() {
        enterIdle();
    }

    public void shutdown() {
        cancelStartCountdown();
        cancelRoundEndTask();
    }

    private void applyCurrentState(Player player, BorderNotification notification) {
        if (roundState == RoundState.IDLE) {
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
            return;
        }

        if (roundState == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            borderRenderer.applyLobbyBorder(player);
            return;
        }

        if (roundState == RoundState.COUNTDOWN) {
            if (!startCandidateIds.contains(player.getUniqueId())) {
                enterSpectator(player, notification);
                return;
            }
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
            return;
        }

        if (roundPlayers.isSpectator(player)) {
            applySpectator(player, notification);
            return;
        }

        if (!roundPlayers.isRoundPlayer(player)) {
            enterSpectator(player, notification);
            return;
        }

        luckPermsRoleService.markActive(player);
        apply(player, notification);
    }

    private void ensureRoundPlayer(Player player) {
        if (roundState != RoundState.ACTIVE
                || roundPlayers.isSpectator(player)
                || roundPlayers.isRoundPlayer(player)) {
            return;
        }

        enterSpectator(player, BorderNotification.NONE);
    }

    private void apply(Player player, BorderNotification notification) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = playerBorderDataService.updateMaxReachedLevel(player, playerBorderDataService.getOrCreate(player));
        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    private void apply(Player player, PlayerBorderData data, int currentLevel, BorderNotification notification) {
        int borderLevel = playerBorderDataService.resolveLevelForBorder(data, currentLevel);
        double size = sizeCalculator.calculate(borderLevel);
        if (allowsPersonalBorder(player)) {
            size = borderRenderer.apply(player, data, borderLevel, notification);
        } else {
            borderRenderer.resetToGlobal(player);
            playerBorderDataService.save(data.withLastAppliedBorderSize(size));
        }
        if (!Double.isNaN(size)) {
            checkTargetBorder(player, size);
        }
    }

    private boolean allowsPersonalBorder(Player player) {
        DimensionPolicy policy = settings.dimensionPolicy();
        return policy.allowsPersonalBorder(player.getWorld());
    }

    private void applySpectator(Player player, BorderNotification notification) {
        borderRenderer.applySpectator(player, notification);
        luckPermsRoleService.markSpectator(player);
    }

    private void enterSpectator(Player player, BorderNotification notification) {
        roundPlayers.markSpectator(player);
        applySpectator(player, notification);
    }

    private void showSpreadOutToStartCandidates(int countdownSeconds) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (startCandidateIds.contains(player.getUniqueId())) {
                notifier.showSpreadOut(player, countdownSeconds);
            }
        }
    }

    private void broadcastCountdown(int remainingSeconds) {
        String message = messages.text(
                "service.countdown",
                Messages.placeholder("seconds", remainingSeconds)
        );
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(message);
            if (remainingSeconds <= 3) {
                notifier.showCountdown(player, remainingSeconds);
            }
        }
    }

    private void activateRound() {
        roundState = RoundState.ACTIVE;
        roundPlayers.clearPlayerStates();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (roundState != RoundState.ACTIVE) {
                break;
            }
            if (!startCandidateIds.contains(player.getUniqueId())) {
                enterSpectator(player, BorderNotification.SPECTATOR);
                continue;
            }
            preparePlayerForRoundStart(player);
            activatePlayerFromCurrentPosition(player, BorderNotification.JOIN);
        }

        startCandidateIds.clear();

        if (roundState == RoundState.ACTIVE) {
            scheduleRoundEnd();
            checkEliminationWinner();
        }
    }

    private void preparePlayerForRoundStart(Player player) {
        if (settings.resetXpOnStart()) {
            player.setExp(0.0F);
            player.setLevel(0);
            player.setTotalExperience(0);
        }

        if (settings.clearInventoryOnStart()) {
            player.getInventory().clear();
        }
    }

    private void activatePlayerFromCurrentPosition(Player player, BorderNotification notification) {
        roundPlayers.activate(player);
        luckPermsRoleService.markActive(player);
        PlayerBorderData data = playerBorderDataService.createInitial(player, Math.max(0, player.getLevel()));
        playerBorderDataService.save(data);
        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    private boolean isInsideLobbyBorder(Player player) {
        double lobbyRadius = settings.lobbyRadiusBlocks();
        if (lobbyRadius <= 0.0D) {
            return true;
        }

        Location playerLocation = player.getLocation();
        Location lobbyCenter = player.getWorld().getSpawnLocation();
        return Math.abs(playerLocation.getX() - lobbyCenter.getX()) <= lobbyRadius
                && Math.abs(playerLocation.getZ() - lobbyCenter.getZ()) <= lobbyRadius;
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

        List<PlayerScore> winners = roundScores.findWinners(plugin.getServer().getOnlinePlayers(), this::isActive);
        if (winners.isEmpty()) {
            finishRoundWithoutWinner("service.end-reason-time");
            return;
        }

        if (winners.size() == 1) {
            finishRoundWithWinner(winners.getFirst().player(), "service.end-reason-time");
            return;
        }

        finishRoundWithSharedWinners(winners, "service.end-reason-time");
    }

    private void checkTargetLevel(Player player, int reachedLevel) {
        if (roundState != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.TARGET_LEVEL) {
            return;
        }

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
        showRoundPlacements(winner);
        finishRound();
    }

    private void finishRoundWithSharedWinners(List<PlayerScore> winners, String reasonKey) {
        winners.sort((first, second) -> first.player().getName().compareToIgnoreCase(second.player().getName()));
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-shared-winners",
                Messages.placeholder("winners", joinPlayerNames(winners)),
                Messages.placeholder("reason", reason)
        ));
        showRoundPlacements(null);
        finishRound();
    }

    private void finishRoundWithoutWinner(String reasonKey) {
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-no-winner",
                Messages.placeholder("reason", reason)
        ));
        showRoundPlacements(null);
        finishRound();
    }

    private void finishRound() {
        enterIdle();
    }

    private String joinPlayerNames(List<PlayerScore> scores) {
        StringJoiner joiner = new StringJoiner(", ");
        for (PlayerScore score : scores) {
            joiner.add(score.player().getName());
        }
        return joiner.toString();
    }

    private void showRoundPlacements(Player winner) {
        List<PlayerScore> scores = roundScores.rankedScores(plugin.getServer().getOnlinePlayers(), roundPlayers::isRoundPlayer);
        moveWinnerToFirst(scores, winner);
        PlayerScore previousScore = null;
        int place = 0;

        for (int index = 0; index < scores.size(); index++) {
            PlayerScore score = scores.get(index);
            if (previousScore == null || isWinner(score, winner) || isWinner(previousScore, winner)
                    || roundScores.compareScores(score, previousScore) != 0) {
                place = index + 1;
            }

            notifier.showRoundPlacement(
                    score.player(),
                    place,
                    score.kills(),
                    score.deaths(),
                    score.highestLevel(),
                    score.borderSize()
            );
            previousScore = score;
        }
    }

    private void moveWinnerToFirst(List<PlayerScore> scores, Player winner) {
        if (winner == null) {
            return;
        }

        UUID winnerId = winner.getUniqueId();
        for (int index = 0; index < scores.size(); index++) {
            if (!scores.get(index).player().getUniqueId().equals(winnerId)) {
                continue;
            }

            if (index > 0) {
                scores.add(0, scores.remove(index));
            }
            return;
        }
    }

    private boolean isWinner(PlayerScore score, Player winner) {
        return winner != null && score.player().getUniqueId().equals(winner.getUniqueId());
    }

    private void enterIdle() {
        cancelStartCountdown();
        cancelRoundEndTask();
        roundState = RoundState.IDLE;
        startCandidateIds.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
        }

        roundPlayers.clearRound();
    }

    private void enterLobby(boolean teleportPlayers) {
        cancelStartCountdown();
        cancelRoundEndTask();
        roundState = RoundState.LOBBY;
        startCandidateIds.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            if (teleportPlayers && settings.teleportPlayersToLobbySpawn()) {
                teleportToWorldSpawn(player);
            }
            borderRenderer.applyLobbyBorder(player);
        }

        roundPlayers.clearRound();
    }

    private void teleportToWorldSpawn(Player player) {
        Location spawnLocation = player.getWorld().getSpawnLocation();
        player.teleport(spawnLocation);
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
        return roundPlayers.isActive(roundState, player);
    }
}
