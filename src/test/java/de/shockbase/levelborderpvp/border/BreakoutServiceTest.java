package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BreakoutServiceTest {
    @Test void immediateDisqualificationRecordsOneDeathAndDefersTheKill() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("test");
        when(player.getHealth()).thenReturn(20D);
        Location location = new Location(mock(World.class), 10, 64, 10);
        when(player.getLocation()).thenReturn(location);
        when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        RoundSession session = mock(RoundSession.class);
        when(session.state()).thenReturn(RoundState.ACTIVE);
        RoundPlayerTracker players = new RoundPlayerTracker();
        players.activate(player);
        PersonalBorderGeometry geometry = mock(PersonalBorderGeometry.class);
        when(geometry.isOutsideCurrentPersonalBorder(player, location)).thenReturn(true);
        PlayerStateService states = mock(PlayerStateService.class);
        RoundEndService rules = mock(RoundEndService.class);
        BreakoutService service = new BreakoutService(plugin, mock(LevelBorderSettings.class), mock(Messages.class),
                session, players, mock(BorderNotifier.class), geometry, states, rules);
        service.handlePotentialBreakout(player, location);
        assertTrue(players.isSpectator(player));
        assertEquals(1, players.deaths(player));
        verify(player, never()).setHealth(anyDouble());
        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        verify(session).later(action.capture(), eq(100L));
        action.getValue().run();
        verify(player).setHealth(0D);
        verify(rules).checkRoundEndAfterActivePlayerRemoval("service.end-reason-all-disqualified");
    }
}
