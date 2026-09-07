package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.StartPlacementMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartPlacementServiceTest {
    private final LevelBorderSettings settings = mock(LevelBorderSettings.class);
    private final BorderRenderer renderer = mock(BorderRenderer.class);
    private final SafeLocationService locations = mock(SafeLocationService.class);
    private final World world = mock(World.class);

    private StartPlacementService service() {
        when(settings.startPlacementMode()).thenReturn(StartPlacementMode.GRID);
        when(settings.startGridSpacingBlocks()).thenReturn(64D);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        return new StartPlacementService(settings, renderer, locations);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return player;
    }

    @Test void insufficientLocationsDoNotTeleportAnyone() {
        StartPlacementService service = service();
        Player first = player(), second = player();
        assertFalse(service.placeStartPlayers(List.of(first, second)));
        verify(first, never()).teleport(any(Location.class));
        verify(second, never()).teleport(any(Location.class));
    }

    @Test void rejectedTeleportFailsPlacement() {
        StartPlacementService service = service();
        when(locations.findSafeRespawnLocationInColumn(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> new Location(world, call.getArgument(1, Integer.class) + 0.5D, 64, call.getArgument(2, Integer.class) + 0.5D));
        Player player = player();
        when(player.teleport(any(Location.class))).thenReturn(false);
        assertFalse(service.placeStartPlayers(List.of(player)));
    }

    @Test void redirectedTeleportFailsEvenWhenTheApiReturnsTrue() {
        StartPlacementService service = service();
        when(locations.findSafeRespawnLocationInColumn(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> new Location(world, call.getArgument(1, Integer.class) + 0.5D, 64, call.getArgument(2, Integer.class) + 0.5D));
        Player player = player();
        when(player.teleport(any(Location.class))).thenReturn(true);
        assertFalse(service.placeStartPlayers(List.of(player)));
    }
}
