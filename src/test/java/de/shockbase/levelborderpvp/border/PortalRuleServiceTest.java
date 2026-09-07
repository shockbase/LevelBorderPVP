package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.DimensionPolicy;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalRuleServiceTest {
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final RoundSession session = mock(RoundSession.class);
    private final RoundPlayerTracker players = new RoundPlayerTracker();
    private final PlayerBorderDataService data = mock(PlayerBorderDataService.class);
    private final PersonalBorderGeometry geometry = mock(PersonalBorderGeometry.class);
    private PortalRuleService service;

    @BeforeEach void setup() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(session.state()).thenReturn(RoundState.ACTIVE);
        players.activate(player);
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        when(settings.dimensionPolicy()).thenReturn(DimensionPolicy.SAFE_PVE);
        service = new PortalRuleService(settings, mock(Messages.class), players, session, data, geometry);
    }

    @Test void redirectsOnlyToAStoredPortalStillInsideTheBorder() {
        Location requested = new Location(world, 100, 64, 100);
        Location stored = new Location(world, 0, 64, 0);
        when(data.findOverworldPortal(player, world)).thenReturn(stored);
        when(geometry.isInsidePersonalBorder(player, stored)).thenReturn(true);
        assertSame(stored, service.resolveOverworldPortalReturn(player, requested));
        when(geometry.isInsidePersonalBorder(player, stored)).thenReturn(false);
        assertNull(service.resolveOverworldPortalReturn(player, requested));
    }

    @Test void portalRadiusIsLimitedByTheNearestBorderEdge() {
        PlayerBorderData border = new PlayerBorderData(player.getUniqueId(), UUID.randomUUID(), "world", 0, 64, 0, 0, 0, 10, null);
        when(data.findExisting(player)).thenReturn(border);
        when(geometry.isSameWorld(border, world)).thenReturn(true);
        when(geometry.borderSize(border, 0)).thenReturn(10D);
        assertEquals(1, service.limitPortalRadiusInsidePersonalBorder(player, new Location(world, 3.2, 64, 0), 128));
        assertEquals(0, service.limitPortalRadiusInsidePersonalBorder(player, new Location(world, 6, 64, 0), 128));
    }

    @Test void spectatorCannotRecordAPortal() {
        players.markSpectator(player);
        assertFalse(service.rememberFirstOverworldPortal(player, new Location(world, 0, 64, 0)));
        verifyNoInteractions(data);
    }
}
