package de.shockbase.levelborderpvp.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record PlayerBorderData(
        UUID playerId,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        int maxReachedLevel,
        int killBonusLevels,
        double lastAppliedBorderSize,
        PortalLocationData overworldPortal
) {

    public PlayerBorderData withMaxReachedLevel(int newMaxReachedLevel) {
        return new PlayerBorderData(
                playerId,
                worldId,
                worldName,
                x,
                y,
                z,
                newMaxReachedLevel,
                killBonusLevels,
                lastAppliedBorderSize,
                overworldPortal
        );
    }

    public PlayerBorderData withKillBonusLevels(int newKillBonusLevels) {
        return new PlayerBorderData(
                playerId,
                worldId,
                worldName,
                x,
                y,
                z,
                maxReachedLevel,
                newKillBonusLevels,
                lastAppliedBorderSize,
                overworldPortal
        );
    }

    public PlayerBorderData withLastAppliedBorderSize(double newLastAppliedBorderSize) {
        return new PlayerBorderData(
                playerId,
                worldId,
                worldName,
                x,
                y,
                z,
                maxReachedLevel,
                killBonusLevels,
                newLastAppliedBorderSize,
                overworldPortal
        );
    }

    public PlayerBorderData withOverworldPortal(PortalLocationData newOverworldPortal) {
        return new PlayerBorderData(
                playerId,
                worldId,
                worldName,
                x,
                y,
                z,
                maxReachedLevel,
                killBonusLevels,
                lastAppliedBorderSize,
                newOverworldPortal
        );
    }

    public Location toLocation(World fallbackWorld) {
        World world = Bukkit.getWorld(worldId);
        if (world == null && worldName != null) {
            world = Bukkit.getWorld(worldName);
        }
        if (world == null) {
            world = fallbackWorld;
        }
        return new Location(world, x, y, z);
    }
}
