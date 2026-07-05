package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.DimensionPolicy;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import de.shockbase.levelborderpvp.integration.PlayerRollbackService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class BorderService {

    private static final long DISQUALIFICATION_DEATH_DELAY_TICKS = 5L * 20L;

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final LuckPermsRoleService luckPermsRoleService;
    private final PlayerRollbackService rollbackService;
    private final BorderRenderer borderRenderer;
    private final PlayerBorderDataService playerBorderDataService;
    private final BorderSizeCalculator sizeCalculator;
    private final BorderNotifier notifier;
    private final RoundPlayerTracker roundPlayers = new RoundPlayerTracker();
    private final Set<UUID> startCandidateIds = new HashSet<>();
    private final Map<UUID, BukkitTask> breakoutTasks = new HashMap<>();
    private final RoundScoreTracker roundScores;

    private RoundState roundState = RoundState.IDLE;
    private BukkitTask startCountdownTask;
    private BukkitTask roundEndTask;

    public record StartResult(boolean started, int eligiblePlayers, int requiredPlayers) {
    }

    public BorderService(
            Plugin plugin,
            WorldBorderApi worldBorderApi,
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            BorderNotifier notifier,
            Messages messages,
            LuckPermsRoleService luckPermsRoleService,
            PlayerRollbackService rollbackService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.luckPermsRoleService = luckPermsRoleService;
        this.rollbackService = rollbackService;
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

    public void handleWorldChange(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyWorldChangeState(player));
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
        cancelAllBreakoutTasks();
        scheduleRoundEnd();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isActive(player)) {
                continue;
            }

            int currentLevel = Math.max(0, player.getLevel());
            PlayerScore score = roundScores.score(player);
            checkTargetLevel(player, settings.usesCurrentLevelMode() ? currentLevel : score.highestLevel());
            checkTargetBorder(player, score.borderSize());
            handlePotentialBreakout(player, player.getLocation());
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

    public boolean isInsidePersonalBorder(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null || !isActiveRoundPlayer(player)) {
            return false;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !isSameWorld(data, location.getWorld())) {
            return false;
        }

        double borderSize = borderSize(data, Math.max(0, player.getLevel()));
        double maxOffset = Math.max(0.0D, borderSize / 2.0D);
        return Math.abs(location.getX() - data.x()) <= maxOffset
                && Math.abs(location.getZ() - data.z()) <= maxOffset;
    }

    public void handlePotentialBreakout(Player player, Location location) {
        if (!isOutsideCurrentPersonalBorder(player, location)) {
            cancelBreakoutTask(player);
            return;
        }

        startBreakoutCountdown(player);
    }

    public boolean rememberFirstOverworldPortal(Player player, Location portalLocation) {
        if (portalLocation == null
                || portalLocation.getWorld() == null
                || portalLocation.getWorld().getEnvironment() != World.Environment.NORMAL
                || !shouldApplyPortalRules(player)
                || !isInsidePersonalBorder(player, portalLocation)) {
            return false;
        }

        return playerBorderDataService.saveFirstOverworldPortal(player, portalLocation);
    }

    public Location resolveOverworldPortalReturn(Player player, Location targetLocation) {
        if (targetLocation == null
                || targetLocation.getWorld() == null
                || targetLocation.getWorld().getEnvironment() != World.Environment.NORMAL
                || !shouldApplyPortalRules(player)) {
            return null;
        }

        if (isInsidePersonalBorder(player, targetLocation)) {
            return targetLocation;
        }

        Location storedPortal = playerBorderDataService.findOverworldPortal(player, targetLocation.getWorld());
        if (storedPortal == null || storedPortal.getWorld() == null) {
            return null;
        }
        if (storedPortal.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return null;
        }
        if (!isInsidePersonalBorder(player, storedPortal)) {
            return null;
        }

        return storedPortal;
    }

    public int limitPortalRadiusInsidePersonalBorder(Player player, Location center, int requestedRadius) {
        if (requestedRadius <= 0 || center == null || center.getWorld() == null || !isActiveRoundPlayer(player)) {
            return 0;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !isSameWorld(data, center.getWorld())) {
            return 0;
        }

        double borderSize = borderSize(data, Math.max(0, player.getLevel()));
        double halfSize = Math.max(0.0D, borderSize / 2.0D);
        double maxXRadius = halfSize - Math.abs(center.getX() - data.x());
        double maxZRadius = halfSize - Math.abs(center.getZ() - data.z());
        int allowedRadius = (int) Math.floor(Math.max(0.0D, Math.min(maxXRadius, maxZRadius)));
        return Math.max(0, Math.min(requestedRadius, allowedRadius));
    }

    public void showPortalBlocked(Player player) {
        player.sendMessage(messages.text("service.portal-blocked"));
    }

    public void showPortalReturnRedirected(Player player) {
        player.sendMessage(messages.text("service.portal-return-redirected"));
    }

    public void showPortalMissing(Player player) {
        player.sendMessage(messages.text("service.portal-missing"));
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
        if (settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        boolean eliminatedByPlayer = isActivePlayerKill(killer, player);
        enterSpectator(player, eliminatedByPlayer ? BorderNotification.NONE : BorderNotification.SPECTATOR);
        if (eliminatedByPlayer) {
            notifier.showEliminated(player, killer.getName());
        }
        checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
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

    public StartResult start(int countdownSeconds) {
        List<Player> selectedStartPlayers = findStartCandidates();
        int minimumStartPlayers = settings.minimumStartPlayers();
        if (selectedStartPlayers.size() < minimumStartPlayers) {
            return new StartResult(false, selectedStartPlayers.size(), minimumStartPlayers);
        }

        markRoundEndedIfActive();
        cancelStartCountdown();
        cancelRoundEndTask();
        cancelAllBreakoutTasks();

        int boundedCountdownSeconds = Math.max(0, Math.min(countdownSeconds, settings.maxStartCountdownSeconds()));
        roundState = RoundState.COUNTDOWN;
        roundPlayers.clearRound();
        startCandidateIds.clear();
        for (Player player : selectedStartPlayers) {
            startCandidateIds.add(player.getUniqueId());
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            if (startCandidateIds.contains(player.getUniqueId())) {
                borderRenderer.resetToGlobal(player);
            } else {
                enterSpectator(player, BorderNotification.SPECTATOR);
            }
        }

        if (boundedCountdownSeconds <= 0) {
            activateRound();
            return new StartResult(true, selectedStartPlayers.size(), minimumStartPlayers);
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
        return new StartResult(true, selectedStartPlayers.size(), minimumStartPlayers);
    }

    public void lobby() {
        enterLobby(true);
    }

    public void stop() {
        if (roundState == RoundState.ACTIVE && settings.rollbackOnRoundEnd()) {
            rollbackService.markRoundEnded();
            announceAutomaticRollback(rollbackService.rollbackConfiguredProvider());
        }
        enterIdle();
    }

    public PlayerRollbackService.RollbackResult rollbackRoundChanges(String requestedProvider) {
        return rollbackService.rollback(requestedProvider);
    }

    public void shutdown() {
        cancelStartCountdown();
        cancelRoundEndTask();
        cancelAllBreakoutTasks();
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

    private void applyWorldChangeState(Player player) {
        if (roundPlayers.isSpectator(player)) {
            applySpectator(player, BorderNotification.NONE);
            return;
        }
        if (!isActive(player)) {
            return;
        }

        luckPermsRoleService.markActive(player);
        apply(player, BorderNotification.NONE);
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

    private boolean isSameWorld(PlayerBorderData data, World world) {
        if (data.worldId().equals(world.getUID())) {
            return true;
        }
        return data.worldName() != null && data.worldName().equals(world.getName());
    }

    private void applySpectator(Player player, BorderNotification notification) {
        borderRenderer.applySpectator(player, notification);
        luckPermsRoleService.markSpectator(player);
    }

    private void enterSpectator(Player player, BorderNotification notification) {
        cancelBreakoutTask(player);
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

        List<Player> activeStartPlayers = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (startCandidateIds.contains(player.getUniqueId())) {
                activeStartPlayers.add(player);
            }
        }
        rollbackService.beginRound(activeStartPlayers);

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
            checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
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

    private List<Player> findStartCandidates() {
        List<Player> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isInsideLobbyBorder(player)) {
                players.add(player);
            }
        }
        return players;
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

        int activePlayers = countActivePlayers();
        if (activePlayers == 0) {
            finishRoundWithoutWinner("service.end-reason-elimination");
            return;
        }
        if (activePlayers != 1) {
            return;
        }

        Player remaining = null;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isActive(player)) {
                continue;
            }
            remaining = player;
        }

        if (remaining != null) {
            finishRoundWithWinner(remaining, "service.end-reason-elimination");
        }
    }

    private void startBreakoutCountdown(Player player) {
        UUID playerId = player.getUniqueId();
        if (breakoutTasks.containsKey(playerId)) {
            return;
        }

        int graceSeconds = settings.breakoutGraceSeconds();
        if (graceSeconds <= 0) {
            disqualifyForBreakout(player);
            return;
        }

        BukkitRunnable countdown = new BukkitRunnable() {
            private int remainingSeconds = graceSeconds;

            @Override
            public void run() {
                if (!player.isOnline() || !isOutsideCurrentPersonalBorder(player, player.getLocation())) {
                    cancelBreakoutTask(player);
                    return;
                }

                if (remainingSeconds <= 0) {
                    breakoutTasks.remove(playerId);
                    disqualifyForBreakout(player);
                    cancel();
                    return;
                }

                notifier.showBreakoutWarning(player, remainingSeconds);
                remainingSeconds--;
            }
        };

        breakoutTasks.put(playerId, countdown.runTaskTimer(plugin, 0L, 20L));
    }

    private boolean isOutsideCurrentPersonalBorder(Player player, Location location) {
        if (player == null
                || location == null
                || location.getWorld() == null
                || !isActive(player)
                || !settings.dimensionPolicy().allowsPersonalBorder(location.getWorld())) {
            return false;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !isSameWorld(data, location.getWorld())) {
            return false;
        }

        double borderSize = borderSize(data, Math.max(0, player.getLevel()));
        double maxOffset = Math.max(0.0D, borderSize / 2.0D);
        return Math.abs(location.getX() - data.x()) > maxOffset
                || Math.abs(location.getZ() - data.z()) > maxOffset;
    }

    private void disqualifyForBreakout(Player player) {
        cancelBreakoutTask(player);
        if (!isActive(player)) {
            return;
        }

        roundPlayers.recordDeath(player);
        roundPlayers.markSpectator(player);
        applySpectator(player, BorderNotification.NONE);
        notifier.showDisqualified(player);
        plugin.getServer().broadcastMessage(messages.text(
                "service.player-disqualified",
                Messages.placeholder("player", player.getName())
        ));

        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishDisqualification(playerId), DISQUALIFICATION_DEATH_DELAY_TICKS);
    }

    private void finishDisqualification(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && roundPlayers.isSpectator(player)) {
            Location location = player.getLocation();
            if (location.getWorld() != null) {
                location.getWorld().strikeLightningEffect(location);
            }
            notifier.playDisqualificationLightning(player);
            if (!player.isDead() && player.getHealth() > 0.0D) {
                player.setHealth(0.0D);
            }
        }

        checkRoundEndAfterActivePlayerRemoval("service.end-reason-all-disqualified");
    }

    private void checkRoundEndAfterActivePlayerRemoval(String noActivePlayersReasonKey) {
        if (roundState != RoundState.ACTIVE) {
            return;
        }

        if (countActivePlayers() == 0) {
            finishRoundWithoutWinner(noActivePlayersReasonKey);
            return;
        }

        checkEliminationWinner();
    }

    private int countActivePlayers() {
        return roundPlayers.activeCount(roundState);
    }

    private void cancelBreakoutTask(Player player) {
        BukkitTask task = breakoutTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllBreakoutTasks() {
        for (BukkitTask task : breakoutTasks.values()) {
            task.cancel();
        }
        breakoutTasks.clear();
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
        rollbackService.markRoundEnded();
        if (settings.rollbackOnRoundEnd()) {
            announceAutomaticRollback(rollbackService.rollbackConfiguredProvider());
        }
        enterIdle();
    }

    private void announceAutomaticRollback(PlayerRollbackService.RollbackResult result) {
        if (result.status() == PlayerRollbackService.RollbackStatus.STARTED) {
            plugin.getServer().broadcastMessage(messages.text(
                    "service.rollback-started",
                    Messages.placeholder("provider", result.provider()),
                    Messages.placeholder("players", result.players()),
                    Messages.placeholder("commands", result.commands())
            ));
            return;
        }

        plugin.getLogger().warning(messages.text(
                "log.rollback-skipped",
                Messages.placeholder("status", result.status()),
                Messages.placeholder("provider", result.provider())
        ));
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
        markRoundEndedIfActive();
        cancelStartCountdown();
        cancelRoundEndTask();
        cancelAllBreakoutTasks();
        roundState = RoundState.IDLE;
        startCandidateIds.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
        }

        roundPlayers.clearRound();
    }

    private void enterLobby(boolean teleportPlayers) {
        markRoundEndedIfActive();
        cancelStartCountdown();
        cancelRoundEndTask();
        cancelAllBreakoutTasks();
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

    private void markRoundEndedIfActive() {
        if (roundState == RoundState.ACTIVE) {
            rollbackService.markRoundEnded();
        }
    }

    private boolean isActive(Player player) {
        return roundPlayers.isActive(roundState, player);
    }
}
