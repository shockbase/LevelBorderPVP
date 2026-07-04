package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

final class RoundScoreTracker {

    private final LevelBorderSettings settings;
    private final BorderSizeCalculator sizeCalculator;
    private final PlayerBorderDataService playerBorderDataService;
    private final RoundPlayerTracker roundPlayers;

    RoundScoreTracker(
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            PlayerBorderDataService playerBorderDataService,
            RoundPlayerTracker roundPlayers
    ) {
        this.settings = settings;
        this.sizeCalculator = sizeCalculator;
        this.playerBorderDataService = playerBorderDataService;
        this.roundPlayers = roundPlayers;
    }

    PlayerScore findWinner(Collection<? extends Player> players, Predicate<Player> isActive) {
        PlayerScore best = null;
        boolean tied = false;

        for (Player player : players) {
            if (!isActive.test(player)) {
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

    List<PlayerScore> rankedScores(Collection<? extends Player> players, Predicate<Player> isIncluded) {
        List<PlayerScore> scores = new ArrayList<>();
        for (Player player : players) {
            if (isIncluded.test(player)) {
                scores.add(score(player));
            }
        }

        scores.sort((first, second) -> compareScores(second, first));
        return scores;
    }

    PlayerScore score(Player player) {
        PlayerBorderData data = playerBorderDataService.updateMaxReachedLevel(player, playerBorderDataService.getOrCreate(player));
        int currentLevel = Math.max(0, player.getLevel());
        int borderLevel = playerBorderDataService.resolveLevelForBorder(data, currentLevel);
        return new PlayerScore(
                player,
                sizeCalculator.calculate(borderLevel),
                Math.max(data.maxReachedLevel(), currentLevel),
                roundPlayers.kills(player),
                roundPlayers.deaths(player)
        );
    }

    int compareScores(PlayerScore first, PlayerScore second) {
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
}
