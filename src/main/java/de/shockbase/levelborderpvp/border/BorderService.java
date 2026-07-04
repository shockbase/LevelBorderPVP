package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class BorderService {

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final LuckPermsRoleService luckPermsRoleService;
    private final BorderRenderer borderRenderer;
    private final PlayerBorderDataService playerBorderDataService;
    private final RoundPlayerTracker roundPlayers = new RoundPlayerTracker();
    private final RoundScoreTracker roundScores;

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
        this.settings = settings;
        this.messages = messages;
        this.luckPermsRoleService = luckPermsRoleService;
        this.borderRenderer = new BorderRenderer(plugin, worldBorderApi, playerBorderRepository, settings, sizeCalculator, notifier);
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

        roundPlayers.recordKill(killer);

        if (settings.usesCurrentLevelMode() || !settings.highestKillBonusEnabled()) {
            return;
        }

        ensureRoundPlayer(killer);
        ensureRoundPlayer(killed);

        PlayerBorderData killedData = playerBorderDataService.updateMaxReachedLevel(killed, playerBorderDataService.getOrCreate(killed));
        int bonusLevels = Math.max(0, killedData.maxReachedLevel());
        if (settings.highestKillBonusInheritsVictimBonus()) {
            bonusLevels = playerBorderDataService.addLevels(bonusLevels, killedData.killBonusLevels());
        }
        if (bonusLevels <= 0) {
            return;
        }

        PlayerBorderData killerData = playerBorderDataService.updateMaxReachedLevel(killer, playerBorderDataService.getOrCreate(killer));
        int newKillBonusLevels = playerBorderDataService.addLevels(killerData.killBonusLevels(), bonusLevels);
        if (newKillBonusLevels == killerData.killBonusLevels()) {
            return;
        }

        killerData = killerData.withKillBonusLevels(newKillBonusLevels);
        playerBorderDataService.save(killerData);
        apply(killer, killerData, Math.max(0, killer.getLevel()), BorderNotification.KILL_BONUS);
    }

    public void handlePlayerDeath(Player player) {
        if (!isActive(player)) {
            return;
        }

        roundPlayers.recordDeath(player);
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
        roundPlayers.clearRound();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            borderRenderer.cancelAnimation(player);
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
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
        borderRenderer.cancelAnimation(player);
        roundPlayers.stop(player);
        luckPermsRoleService.clear(player);
        borderRenderer.resetToGlobal(player);
    }

    public void reset(Player player) {
        borderRenderer.cancelAnimation(player);
        roundPlayers.reset(player);

        PlayerBorderData data = playerBorderDataService.createInitial(player, Math.max(0, player.getLevel()));
        playerBorderDataService.save(data);

        if (roundState == RoundState.ACTIVE) {
            roundPlayers.activate(player);
            luckPermsRoleService.markActive(player);
            apply(player, data, Math.max(0, player.getLevel()), BorderNotification.JOIN);
        } else if (roundState == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            borderRenderer.applyLobbyBorder(player);
        } else {
            borderRenderer.resetToGlobal(player);
        }
    }

    public void shutdown() {
        cancelStartCountdown();
        cancelRoundEndTask();
        borderRenderer.shutdown();
    }

    private void applyCurrentState(Player player, BorderNotification notification) {
        if (roundState == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            borderRenderer.applyLobbyBorder(player);
            return;
        }

        if (roundState == RoundState.COUNTDOWN) {
            borderRenderer.cancelAnimation(player);
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
            return;
        }

        if (roundPlayers.isSpectator(player)) {
            applySpectator(player, notification);
            return;
        }

        if (roundPlayers.isManuallyStopped(player)) {
            borderRenderer.resetToGlobal(player);
            return;
        }

        if (!roundPlayers.isRoundPlayer(player)) {
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
        if (roundState != RoundState.ACTIVE
                || roundPlayers.isManuallyStopped(player)
                || roundPlayers.isSpectator(player)
                || roundPlayers.isRoundPlayer(player)) {
            return;
        }

        if (usesSpectatorForLateJoiners()) {
            enterSpectator(player, BorderNotification.NONE);
            return;
        }
        activatePlayerFromCurrentPosition(player, BorderNotification.NONE);
    }

    private boolean usesSpectatorForLateJoiners() {
        return settings.spectatorModeEnabled() || settings.endCondition() == RoundEndCondition.ELIMINATION;
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
        double size = borderRenderer.apply(player, data, borderLevel, notification);
        if (!Double.isNaN(size)) {
            checkTargetBorder(player, size);
        }
    }

    private void applySpectator(Player player, BorderNotification notification) {
        borderRenderer.applySpectator(player, notification);
        luckPermsRoleService.markSpectator(player);
    }

    private void enterSpectator(Player player, BorderNotification notification) {
        roundPlayers.markSpectator(player);
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
        roundPlayers.clearPlayerStates();

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
        roundPlayers.activate(player);
        luckPermsRoleService.markActive(player);
        PlayerBorderData data = playerBorderDataService.createInitial(player, Math.max(0, player.getLevel()));
        playerBorderDataService.save(data);
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

        PlayerScore winner = roundScores.findWinner(plugin.getServer().getOnlinePlayers(), this::isActive);
        if (winner == null) {
            finishRoundWithoutWinner("service.end-reason-time");
            return;
        }

        finishRoundWithWinner(winner.player(), "service.end-reason-time");
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
            borderRenderer.cancelAnimation(player);
            luckPermsRoleService.clear(player);
            borderRenderer.applyLobbyBorder(player);
        }

        roundPlayers.clearRound();
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
