package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.PlayerRollbackService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/** Formats and announces winners, placements and rollback results. */
final class RoundResultService {
    private final Plugin plugin;
    private final Messages messages;
    private final BorderNotifier notifier;
    private final RoundPlayerTracker roundPlayers;
    private final RoundScoreTracker roundScores;
    private final Runnable onFinished;

    RoundResultService(
            Plugin plugin,
            Messages messages,
            BorderNotifier notifier,
            RoundPlayerTracker roundPlayers,
            RoundScoreTracker roundScores,
            Runnable onFinished
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.notifier = notifier;
        this.roundPlayers = roundPlayers;
        this.roundScores = roundScores;
        this.onFinished = onFinished;
    }

    void finishRoundWithWinner(Player winner, String reasonKey) {
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-winner",
                Messages.placeholder("winner", winner.getName()),
                Messages.placeholder("reason", reason)
        ));
        showRoundPlacements(winner);
        onFinished.run();
    }

    void finishRoundWithSharedWinners(List<PlayerScore> winners, String reasonKey) {
        winners.sort((first, second) -> first.player().getName().compareToIgnoreCase(second.player().getName()));
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-shared-winners",
                Messages.placeholder("winners", joinPlayerNames(winners)),
                Messages.placeholder("reason", reason)
        ));
        showRoundPlacements(null);
        onFinished.run();
    }

    void finishRoundWithoutWinner(String reasonKey) {
        String reason = messages.text(reasonKey);
        plugin.getServer().broadcastMessage(messages.text(
                "service.round-ended-no-winner",
                Messages.placeholder("reason", reason)
        ));
        showRoundPlacements(null);
        onFinished.run();
    }

    void announceAutomaticRollback(PlayerRollbackService.RollbackResult result) {
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

}
