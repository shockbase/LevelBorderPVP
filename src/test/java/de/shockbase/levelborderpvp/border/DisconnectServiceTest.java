package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.EliminationDisconnectPolicy;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisconnectServiceTest {
    private final LevelBorderSettings settings = mock(LevelBorderSettings.class);
    private final RoundSession session = mock(RoundSession.class);
    private final RoundPlayerTracker players = new RoundPlayerTracker();
    private final RoundEndService rules = mock(RoundEndService.class);
    private final Player player = mock(Player.class);
    private final BukkitTask task = mock(BukkitTask.class);
    private DisconnectService service;

    @BeforeEach void setup() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(mock(Server.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("test");
        when(session.state()).thenReturn(RoundState.ACTIVE);
        when(session.later(any(Runnable.class), anyLong())).thenReturn(task);
        when(settings.endCondition()).thenReturn(RoundEndCondition.ELIMINATION);
        when(settings.eliminationDisconnectPolicy()).thenReturn(EliminationDisconnectPolicy.GRACE_PERIOD);
        when(settings.eliminationReconnectGraceSeconds()).thenReturn(60);
        players.activate(player);
        service = new DisconnectService(plugin, settings, mock(Messages.class), session, players, rules);
    }

    @Test void rejoinCancelsPendingEliminationEvenIfCallbackIsDelivered() {
        service.handlePlayerQuit(player);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(session).later(callback.capture(), eq(1200L));
        assertTrue(service.cancelPending(player));
        callback.getValue().run();
        assertFalse(players.isSpectator(player));
        assertEquals(0, players.deaths(player));
        verify(task).cancel();
        verifyNoInteractions(rules);
    }

    @Test void changingPolicyEliminatesPendingPlayerExactlyOnce() {
        service.handlePlayerQuit(player);
        when(settings.eliminationDisconnectPolicy()).thenReturn(EliminationDisconnectPolicy.ELIMINATE);
        service.refreshPendingDisconnects();
        service.refreshPendingDisconnects();
        assertTrue(players.isSpectator(player));
        assertEquals(1, players.deaths(player));
        verify(rules).checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
    }
}
