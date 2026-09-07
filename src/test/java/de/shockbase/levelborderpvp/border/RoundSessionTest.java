package de.shockbase.levelborderpvp.border;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoundSessionTest {
    @Test void oldDelayedActionCannotRunInNewRoundEvenIfAlreadyQueued() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);
        RoundSession session = new RoundSession(plugin);
        AtomicInteger deaths = new AtomicInteger();
        session.transition(RoundState.ACTIVE);
        session.later(deaths::incrementAndGet, 100);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), callback.capture(), eq(100L));
        session.reset();
        session.transition(RoundState.COUNTDOWN);
        session.transition(RoundState.ACTIVE);
        callback.getValue().run();
        assertEquals(0, deaths.get());
        verify(task).cancel();
    }

    @Test void elapsedTimeSurvivesRefreshAndTickCounterOverflow() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        AtomicInteger tick = new AtomicInteger(Integer.MAX_VALUE - 20);
        when(server.getCurrentTick()).thenAnswer(ignored -> tick.get());
        RoundSession session = new RoundSession(plugin);
        session.transition(RoundState.ACTIVE);
        tick.addAndGet(100);
        session.transition(RoundState.ACTIVE);
        assertEquals(100, session.elapsedTicks());
    }
}
