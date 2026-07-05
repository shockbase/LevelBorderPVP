package de.shockbase.levelborderpvp.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record PortalLocationData(
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public static PortalLocationData fromLocation(Location location) {
        World world = location.getWorld();
        return new PortalLocationData(
                world.getUID(),
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public Location toLocation(World fallbackWorld) {
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world == null && worldName != null) {
            world = Bukkit.getWorld(worldName);
        }
        if (world == null) {
            world = fallbackWorld;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
