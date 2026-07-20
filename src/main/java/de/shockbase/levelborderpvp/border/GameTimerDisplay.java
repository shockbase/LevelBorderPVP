package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.i18n.Messages;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class GameTimerDisplay {

    private static final String OBJECTIVE_NAME = "levelborder_timer";
    private static final String TIMER_ENTRY = "levelborder_timer_value";
    private static final String BORDER_ENTRY = "levelborder_border_value";
    private static final String COUNTER_ENTRY = "levelborder_counter_value";

    private final Plugin plugin;
    private final Messages messages;
    private final StatsProvider statsProvider;
    private final Map<UUID, SidebarDisplay> sidebarDisplays = new HashMap<>();

    private TimerPhase phase = TimerPhase.HIDDEN;
    private RoundEndCondition endCondition = RoundEndCondition.DISABLED;
    private long shownSeconds;
    private long roundDurationSeconds;
    private long roundElapsedSeconds;
    private long countdownElapsedSeconds;
    private BukkitTask updateTask;

    GameTimerDisplay(Plugin plugin, Messages messages, StatsProvider statsProvider) {
        this.plugin = plugin;
        this.messages = messages;
        this.statsProvider = statsProvider;
    }

    void startCountdown(int seconds, RoundEndCondition condition) {
        cancelUpdateTask();
        phase = TimerPhase.COUNTDOWN;
        endCondition = condition;
        shownSeconds = Math.max(0, seconds);
        updateOnlinePlayers();
    }

    void updateCountdown(int remainingSeconds) {
        if (phase != TimerPhase.COUNTDOWN) {
            return;
        }
        shownSeconds = Math.max(0, remainingSeconds);
        updateOnlinePlayers();
    }

    void startRound(RoundEndCondition condition, long durationSeconds) {
        phase = TimerPhase.ROUND;
        endCondition = condition;
        roundDurationSeconds = Math.max(0L, durationSeconds);
        roundElapsedSeconds = 0L;
        countdownElapsedSeconds = 0L;
        shownSeconds = condition == RoundEndCondition.TIMED_SCORE ? roundDurationSeconds : 0L;
        restartUpdateTask();
    }

    void refreshRound(RoundEndCondition condition, long durationSeconds) {
        if (phase != TimerPhase.ROUND) {
            return;
        }
        endCondition = condition;
        roundDurationSeconds = Math.max(0L, durationSeconds);
        if (condition == RoundEndCondition.TIMED_SCORE) {
            countdownElapsedSeconds = 0L;
            shownSeconds = roundDurationSeconds;
        } else {
            shownSeconds = roundElapsedSeconds;
        }
        updateOnlinePlayers();
    }

    void showCurrent(Player player) {
        if (phase == TimerPhase.HIDDEN || !player.isOnline()) {
            return;
        }
        updatePlayer(player);
    }

    void hide() {
        cancelUpdateTask();
        phase = TimerPhase.HIDDEN;

        for (Map.Entry<UUID, SidebarDisplay> entry : sidebarDisplays.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            restoreScoreboard(player, entry.getValue());
        }
        sidebarDisplays.clear();
    }

    private void restartUpdateTask() {
        cancelUpdateTask();
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateRound, 0L, 20L);
    }

    private void updateRound() {
        if (phase != TimerPhase.ROUND) {
            cancelUpdateTask();
            return;
        }

        if (endCondition == RoundEndCondition.TIMED_SCORE) {
            shownSeconds = Math.max(0L, roundDurationSeconds - countdownElapsedSeconds);
        } else {
            shownSeconds = roundElapsedSeconds;
        }
        updateOnlinePlayers();
        roundElapsedSeconds++;
        countdownElapsedSeconds++;
    }

    private void updateOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        SidebarDisplay display = sidebarDisplays.get(player.getUniqueId());
        if (display == null || display.objective().getScoreboard() == null) {
            Scoreboard previousScoreboard = display == null || player.getScoreboard() != display.scoreboard()
                    ? player.getScoreboard()
                    : display.previousScoreboard();
            display = createSidebarDisplay(player, previousScoreboard);
            sidebarDisplays.put(player.getUniqueId(), display);
        } else if (player.getScoreboard() != display.scoreboard()) {
            display = new SidebarDisplay(display.scoreboard(), player.getScoreboard(), display.objective());
            sidebarDisplays.put(player.getUniqueId(), display);
            player.setScoreboard(display.scoreboard());
        }

        Objective objective = display.objective();
        objective.displayName(title());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerStats stats = statsProvider.stats(player);
        setLine(objective, TIMER_ENTRY, 3, timerLine());
        setLine(objective, BORDER_ENTRY, 2, borderLine(stats));
        setLine(objective, COUNTER_ENTRY, 1, counterLine(stats));
    }

    private SidebarDisplay createSidebarDisplay(Player player, Scoreboard previousScoreboard) {
        ScoreboardManager scoreboardManager = plugin.getServer().getScoreboardManager();
        Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title());
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
        return new SidebarDisplay(scoreboard, previousScoreboard, objective);
    }

    private void setLine(Objective objective, String entry, int position, Component text) {
        Score score = objective.getScore(entry);
        score.setScore(position);
        score.customName(text);
    }

    private Component title() {
        String key = phase == TimerPhase.COUNTDOWN ? "timer.start-title" : "timer.title";
        return Component.text(messages.text(key), NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private Component timerLine() {
        String labelKey = phase == TimerPhase.COUNTDOWN || endCondition == RoundEndCondition.TIMED_SCORE
                ? "timer.countdown"
                : "timer.round-time";
        return valueLine(messages.text(labelKey), formatTime(shownSeconds), NamedTextColor.YELLOW);
    }

    private Component borderLine(PlayerStats stats) {
        return valueLine(messages.text("timer.border"), stats.borderSize(), NamedTextColor.AQUA);
    }

    private Component counterLine(PlayerStats stats) {
        if (endCondition == RoundEndCondition.ELIMINATION) {
            return valueLine(
                    messages.text("timer.alive"),
                    stats.alivePlayers() + "/" + stats.totalPlayers(),
                    NamedTextColor.GREEN
            );
        }
        return valueLine(messages.text("timer.body-count"), Integer.toString(stats.kills()), NamedTextColor.RED);
    }

    private Component valueLine(String label, String value, NamedTextColor valueColor) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void restoreScoreboard(Player player, SidebarDisplay display) {
        if (player != null && player.isOnline() && player.getScoreboard() == display.scoreboard()) {
            player.setScoreboard(display.previousScoreboard());
        }
        if (display.objective().getScoreboard() != null) {
            display.objective().unregister();
        }
    }

    private void cancelUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    @FunctionalInterface
    interface StatsProvider {
        PlayerStats stats(Player player);
    }

    record PlayerStats(String borderSize, int kills, int alivePlayers, int totalPlayers) {
    }

    private enum TimerPhase {
        HIDDEN,
        COUNTDOWN,
        ROUND
    }

    private record SidebarDisplay(Scoreboard scoreboard, Scoreboard previousScoreboard, Objective objective) {
    }
}
