package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameTimerDisplayTest {
    @Test void refreshingSettingsKeepsElapsedTimeInTheDisplayedCountdown() throws Exception {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        AtomicLong elapsed = new AtomicLong();
        GameTimerDisplay display = new GameTimerDisplay(plugin, mock(Messages.class),
                ignored -> new GameTimerDisplay.PlayerStats("3", 0, 2, 2), elapsed::get);
        display.startRound(RoundEndCondition.TIMED_SCORE, 3600);
        elapsed.set(2400);
        display.refreshRound(RoundEndCondition.TIMED_SCORE, 3600);
        // Inspect the presentation value without a running Paper scoreboard registry.
        var shownSeconds = GameTimerDisplay.class.getDeclaredField("shownSeconds");
        shownSeconds.setAccessible(true);
        assertEquals(1200L, shownSeconds.getLong(display));
        display.refreshRound(RoundEndCondition.TIMED_SCORE, 1800);
        assertEquals(0L, shownSeconds.getLong(display));
    }
}
