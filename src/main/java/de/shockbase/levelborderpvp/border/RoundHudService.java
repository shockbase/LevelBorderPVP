package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Supplies timer statistics, countdown announcements and start rules. */
final class RoundHudService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final RoundScoreTracker roundScores;
    private final BorderNotifier notifier;
    private final LobbyService lobbyService;
    private final BorderSizeFormatter sizeFormatter = new BorderSizeFormatter();
    private final GameTimerDisplay gameTimerDisplay;
    RoundHudService(
            Messages messages,
            Plugin plugin,
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            RoundScoreTracker roundScores,
            BorderNotifier notifier,
            LobbyService lobbyService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.roundScores = roundScores;
        this.notifier = notifier;
        this.lobbyService = lobbyService;
        this.gameTimerDisplay = new GameTimerDisplay(plugin, messages, this::timerStats, () -> session.elapsedTicks() / 20L);
    }

    void showSpreadOutToStartCandidates(int countdownSeconds) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (lobbyService.isCandidate(player)) {
                notifier.showSpreadOut(player, countdownSeconds);
            }
        }
    }

    void broadcastCountdown(int remainingSeconds) {
        gameTimerDisplay.updateCountdown(remainingSeconds);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (remainingSeconds <= 3) {
                notifier.showCountdown(player, remainingSeconds);
            }
        }
    }

    private GameTimerDisplay.PlayerStats timerStats(Player player) {
        boolean isParticipant = session.state() == RoundState.COUNTDOWN
                ? lobbyService.isCandidate(player)
                : roundPlayers.isRoundPlayer(player);
        String borderSize = isParticipant
                ? sizeFormatter.format(roundScores.score(player).borderSize())
                : "-";
        int kills = isParticipant ? roundPlayers.kills(player) : 0;
        int totalPlayers = session.state() == RoundState.COUNTDOWN
                ? lobbyService.candidateCount()
                : roundPlayers.roundPlayerCount();
        int alivePlayers = session.state() == RoundState.COUNTDOWN
                ? totalPlayers
                : roundPlayers.activeCount(session.state());
        return new GameTimerDisplay.PlayerStats(borderSize, kills, alivePlayers, totalPlayers);
    }

    void showRoundRulesToStartCandidates(int countdownSeconds) {
        int displaySeconds = Math.max(1, countdownSeconds - 3);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!lobbyService.isCandidate(player)) {
                continue;
            }

            notifier.showRoundRuleSummary(
                    player,
                    settings.endCondition(),
                    displaySeconds,
                    settings.roundDurationMinutes(),
                    settings.winTargetLevel(),
                    settings.winTargetBorderSizeBlocks()
            );
        }
    }

    void startCountdown(int seconds) { gameTimerDisplay.startCountdown(seconds, settings.endCondition()); }
    void startRound() { gameTimerDisplay.startRound(settings.endCondition(), settings.roundDurationMinutes() * 60L); }
    void refreshRound() { gameTimerDisplay.refreshRound(settings.endCondition(), settings.roundDurationMinutes() * 60L); }
    void showCurrent(Player player) { gameTimerDisplay.showCurrent(player); }
    void hide() { gameTimerDisplay.hide(); }
}
