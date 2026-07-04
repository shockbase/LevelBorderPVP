package de.oehme.personallevelborder.data;

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
        double lastAppliedBorderSize
) {

    public PlayerBorderData withMaxReachedLevel(int newMaxReachedLevel) {
        return new PlayerBorderData(playerId, worldId, worldName, x, y, z, newMaxReachedLevel, killBonusLevels, lastAppliedBorderSize);
    }

    public PlayerBorderData withKillBonusLevels(int newKillBonusLevels) {
        return new PlayerBorderData(playerId, worldId, worldName, x, y, z, maxReachedLevel, newKillBonusLevels, lastAppliedBorderSize);
    }

    public PlayerBorderData withLastAppliedBorderSize(double newLastAppliedBorderSize) {
        return new PlayerBorderData(playerId, worldId, worldName, x, y, z, maxReachedLevel, killBonusLevels, newLastAppliedBorderSize);
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
