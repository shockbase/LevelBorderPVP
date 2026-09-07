package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Evaluates end conditions and schedules the timed round deadline. */
final class RoundEndService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final RoundScoreTracker roundScores;
    private final RoundResultService results;
    private BukkitTask roundEndTask;
    RoundEndService(
            Plugin plugin,
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            RoundScoreTracker roundScores,
            RoundResultService results
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.roundScores = roundScores;
        this.results = results;
    }

    void scheduleRoundEnd() {
        cancelRoundEndTask();
        if (settings.endCondition() != RoundEndCondition.TIMED_SCORE) {
            return;
        }

        long delayTicks = Math.max(1L, settings.roundDurationMinutes() * 60L * 20L - session.elapsedTicks());
        roundEndTask = session.later(this::finishTimedScoreRound, delayTicks);
    }

    private void finishTimedScoreRound() {
        if (session.state() != RoundState.ACTIVE) {
            return;
        }

        List<PlayerScore> winners = roundScores.findWinners(plugin.getServer().getOnlinePlayers(), this::isActive);
        if (winners.isEmpty()) {
            results.finishRoundWithoutWinner("service.end-reason-time");
            return;
        }

        if (winners.size() == 1) {
            results.finishRoundWithWinner(winners.getFirst().player(), "service.end-reason-time");
            return;
        }

        results.finishRoundWithSharedWinners(winners, "service.end-reason-time");
    }

    void checkPlayerTargets(Player player) {
        int currentLevel = Math.max(0, player.getLevel());
        PlayerScore score = roundScores.score(player);
        checkTargetLevel(player, settings.usesCurrentLevelMode() ? currentLevel : score.highestLevel());
        checkTargetBorder(player, score.borderSize());
    }

    void checkTargetLevel(Player player, int reachedLevel) {
        if (session.state() != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.TARGET_LEVEL) {
            return;
        }

        if (reachedLevel >= settings.winTargetLevel()) {
            results.finishRoundWithWinner(player, "service.end-reason-target-level");
        }
    }

    void checkTargetBorder(Player player, double borderSize) {
        if (session.state() != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.TARGET_BORDER) {
            return;
        }

        if (borderSize >= settings.winTargetBorderSizeBlocks()) {
            results.finishRoundWithWinner(player, "service.end-reason-target-border");
        }
    }

    void checkEliminationWinner() {
        if (session.state() != RoundState.ACTIVE || settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        int activePlayers = countActivePlayers();
        if (activePlayers == 0) {
            results.finishRoundWithoutWinner("service.end-reason-elimination");
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
            results.finishRoundWithWinner(remaining, "service.end-reason-elimination");
        }
    }

    void checkRoundEndAfterActivePlayerRemoval(String noActivePlayersReasonKey) {
        if (session.state() != RoundState.ACTIVE) {
            return;
        }

        if (countActivePlayers() == 0) {
            results.finishRoundWithoutWinner(noActivePlayersReasonKey);
            return;
        }

        checkEliminationWinner();
    }

    void cancelRoundEndTask() {
        if (roundEndTask != null) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
    }

    private int countActivePlayers() {
        return roundPlayers.activeCount(session.state());
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
