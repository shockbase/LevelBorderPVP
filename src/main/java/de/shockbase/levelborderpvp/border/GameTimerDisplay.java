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

import java.util.IdentityHashMap;
import java.util.Map;

final class GameTimerDisplay {

    private static final String OBJECTIVE_NAME = "levelborder_timer";
    private static final String TIMER_ENTRY = "levelborder_timer_value";

    private final Plugin plugin;
    private final Messages messages;
    private final Map<Scoreboard, SidebarDisplay> sidebarDisplays = new IdentityHashMap<>();

    private TimerPhase phase = TimerPhase.HIDDEN;
    private RoundEndCondition endCondition = RoundEndCondition.DISABLED;
    private long shownSeconds;
    private long roundDurationSeconds;
    private long roundElapsedSeconds;
    private long countdownElapsedSeconds;
    private BukkitTask updateTask;

    GameTimerDisplay(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    void startCountdown(int seconds) {
        cancelUpdateTask();
        phase = TimerPhase.COUNTDOWN;
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

        for (Map.Entry<Scoreboard, SidebarDisplay> entry : sidebarDisplays.entrySet()) {
            restoreSidebar(entry.getKey(), entry.getValue());
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
        Scoreboard scoreboard = player.getScoreboard();
        SidebarDisplay display = sidebarDisplays.get(scoreboard);
        if (display == null || display.objective().getScoreboard() == null) {
            display = createSidebarDisplay(scoreboard);
            sidebarDisplays.put(scoreboard, display);
        }

        Objective objective = display.objective();
        objective.displayName(Component.text(messages.text("timer.title"), NamedTextColor.GOLD, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Score score = objective.getScore(TIMER_ENTRY);
        score.setScore(1);
        score.customName(timerLine());
    }

    private SidebarDisplay createSidebarDisplay(Scoreboard scoreboard) {
        Objective previousObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
        String objectiveName = availableObjectiveName(scoreboard);
        Objective objective = scoreboard.registerNewObjective(
                objectiveName,
                Criteria.DUMMY,
                Component.text(messages.text("timer.title"), NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        objective.numberFormat(NumberFormat.blank());
        return new SidebarDisplay(objective, previousObjective);
    }

    private String availableObjectiveName(Scoreboard scoreboard) {
        if (scoreboard.getObjective(OBJECTIVE_NAME) == null) {
            return OBJECTIVE_NAME;
        }

        int suffix = 2;
        while (scoreboard.getObjective(OBJECTIVE_NAME + suffix) != null) {
            suffix++;
        }
        return OBJECTIVE_NAME + suffix;
    }

    private Component timerLine() {
        String labelKey = phase == TimerPhase.COUNTDOWN || endCondition == RoundEndCondition.TIMED_SCORE
                ? "timer.countdown"
                : "timer.round-time";
        return Component.text(messages.text(labelKey) + ": ", NamedTextColor.GRAY)
                .append(Component.text(formatTime(shownSeconds), NamedTextColor.YELLOW));
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

    private void restoreSidebar(Scoreboard scoreboard, SidebarDisplay display) {
        Objective objective = display.objective();
        if (objective.getScoreboard() == null) {
            return;
        }

        if (scoreboard.getObjective(DisplaySlot.SIDEBAR) == objective) {
            Objective previousObjective = display.previousObjective();
            if (previousObjective != null && previousObjective.getScoreboard() == scoreboard) {
                previousObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                scoreboard.clearSlot(DisplaySlot.SIDEBAR);
            }
        }
        objective.unregister();
    }

    private void cancelUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private enum TimerPhase {
        HIDDEN,
        COUNTDOWN,
        ROUND
    }

    private record SidebarDisplay(Objective objective, Objective previousObjective) {
    }
}
