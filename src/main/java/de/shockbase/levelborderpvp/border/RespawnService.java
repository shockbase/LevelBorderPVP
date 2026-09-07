package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Selects respawn destinations under the personal border policy. */
final class RespawnService {
    private final LevelBorderSettings settings;
    private final RoundPlayerTracker roundPlayers;
    private final RoundSession session;
    private final PlayerBorderDataService playerBorderDataService;
    private final PersonalBorderGeometry geometry;
    private final SafeLocationService safeLocations;

    RespawnService(
            LevelBorderSettings settings,
            RoundPlayerTracker roundPlayers,
            RoundSession session,
            PlayerBorderDataService playerBorderDataService,
            PersonalBorderGeometry geometry,
            SafeLocationService safeLocations
    ) {
        this.settings = settings;
        this.roundPlayers = roundPlayers;
        this.session = session;
        this.playerBorderDataService = playerBorderDataService;
        this.geometry = geometry;
        this.safeLocations = safeLocations;
    }

    Location resolveSafeRespawnLocation(
            Player player,
            Location vanillaRespawnLocation,
            boolean usesPlayerRespawnBlock,
            boolean missingRespawnBlock
    ) {
        if (player == null
                || vanillaRespawnLocation == null
                || vanillaRespawnLocation.getWorld() == null
                || !isActive(player)) {
            return null;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null) {
            return null;
        }

        Location borderCenter = data.toLocation(player.getWorld());
        World borderWorld = borderCenter.getWorld();
        if (borderWorld == null || !settings.dimensionPolicy().allowsPersonalBorder(borderWorld)) {
            return null;
        }

        Location vanillaValidatedRespawn = usesPlayerRespawnBlock && !missingRespawnBlock
                ? player.getRespawnLocation(true)
                : null;
        if (vanillaValidatedRespawn != null && geometry.isInsidePersonalBorder(player, vanillaValidatedRespawn)) {
            return safeLocations.sameBlockLocation(vanillaValidatedRespawn, vanillaRespawnLocation) ? null : vanillaValidatedRespawn;
        }

        if (!usesPlayerRespawnBlock
                && geometry.isInsidePersonalBorder(player, vanillaRespawnLocation)
                && safeLocations.isSafeRespawnLocation(vanillaRespawnLocation)) {
            return null;
        }

        Location searchStart = usesPlayerRespawnBlock && !missingRespawnBlock
                ? vanillaRespawnLocation
                : borderCenter;
        Location safeLocation = safeLocations.findSafeRespawnLocationInsideBorder(player, data, searchStart);
        if (safeLocation == null || safeLocations.sameBlockLocation(safeLocation, vanillaRespawnLocation)) {
            return null;
        }

        safeLocation.setYaw(vanillaRespawnLocation.getYaw());
        safeLocation.setPitch(vanillaRespawnLocation.getPitch());
        return safeLocation;
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
