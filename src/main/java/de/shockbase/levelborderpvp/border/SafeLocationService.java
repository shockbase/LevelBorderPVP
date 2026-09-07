package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class SafeLocationService {
    private static final int RESPAWN_HORIZONTAL_SEARCH_RADIUS_BLOCKS = 64;
    private static final int RESPAWN_VERTICAL_SEARCH_BLOCKS = 16;
    private static final double RESPAWN_BORDER_MARGIN_BLOCKS = 0.3D;
    private final PlayerBorderDataService dataService;
    private final BorderSizeCalculator sizeCalculator;
    SafeLocationService(PlayerBorderDataService dataService, BorderSizeCalculator sizeCalculator) {
        this.dataService = dataService;
        this.sizeCalculator = sizeCalculator;
    }
    private double borderSize(PlayerBorderData data, int level) {
        return sizeCalculator.calculate(dataService.resolveLevelForBorder(data, level));
    }
    private boolean isSameWorld(PlayerBorderData data, World world) {
        return data.worldId().equals(world.getUID()) || world.getName().equals(data.worldName());
    }
    Location findSafeRespawnLocationInsideBorder(Player player, PlayerBorderData data, Location searchStart) {
        Location borderCenter = data.toLocation(player.getWorld());
        World world = borderCenter.getWorld();
        if (world == null) {
            return null;
        }

        int currentLevel = Math.max(0, player.getLevel());
        double borderSize = borderSize(data, currentLevel);
        Location clampedStart = clampToPersonalBorder(data, world, searchStart, borderSize);
        int startX = clampedStart.getBlockX();
        int startY = clamp(clampedStart.getBlockY(), world.getMinHeight() + 1, world.getMaxHeight() - 2);
        int startZ = clampedStart.getBlockZ();
        int maxSearchRadius = Math.min(RESPAWN_HORIZONTAL_SEARCH_RADIUS_BLOCKS, (int) Math.ceil(borderSize / 2.0D));

        for (int radius = 0; radius <= maxSearchRadius; radius++) {
            for (int x = startX - radius; x <= startX + radius; x++) {
                for (int z = startZ - radius; z <= startZ + radius; z++) {
                    if (radius > 0 && Math.abs(x - startX) != radius && Math.abs(z - startZ) != radius) {
                        continue;
                    }
                    if (!isBlockCenterInsidePersonalBorder(data, world, x, z, borderSize)) {
                        continue;
                    }

                    Location safeLocation = findSafeRespawnLocationInColumn(world, x, z, startY);
                    if (safeLocation != null) {
                        return safeLocation;
                    }
                }
            }
        }

        return null;
    }

    private Location clampToPersonalBorder(PlayerBorderData data, World world, Location location, double borderSize) {
        Location fallback = data.toLocation(world);
        Location source = location != null && location.getWorld() != null && location.getWorld().equals(world)
                ? location
                : fallback;
        double maxOffset = safeBorderOffset(borderSize);
        double x = clamp(source.getX(), data.x() - maxOffset, data.x() + maxOffset);
        double z = clamp(source.getZ(), data.z() - maxOffset, data.z() + maxOffset);
        double y = clamp(source.getY(), world.getMinHeight() + 1.0D, world.getMaxHeight() - 2.0D);
        return new Location(world, x, y, z);
    }

    private double safeBorderOffset(double size) {
        double halfSize = size / 2.0D;
        double safeMargin = Math.min(RESPAWN_BORDER_MARGIN_BLOCKS, Math.max(0.0D, halfSize - 0.05D));
        return Math.max(0.0D, halfSize - safeMargin);
    }

    private boolean isBlockCenterInsidePersonalBorder(PlayerBorderData data, World world, int x, int z, double borderSize) {
        if (!isSameWorld(data, world)) {
            return false;
        }

        double maxOffset = Math.max(0.0D, borderSize / 2.0D);
        return Math.abs((x + 0.5D) - data.x()) <= maxOffset
                && Math.abs((z + 0.5D) - data.z()) <= maxOffset;
    }

    Location findSafeRespawnLocationInColumn(World world, int x, int z, int preferredY) {
        int minimumY = world.getMinHeight() + 1;
        int maximumY = world.getMaxHeight() - 2;
        int clampedY = clamp(preferredY, minimumY, maximumY);

        for (int offset = 0; offset <= RESPAWN_VERTICAL_SEARCH_BLOCKS; offset++) {
            Location higherLocation = safeRespawnLocationAt(world, x, clampedY + offset, z, minimumY, maximumY);
            if (higherLocation != null) {
                return higherLocation;
            }

            if (offset > 0) {
                Location lowerLocation = safeRespawnLocationAt(world, x, clampedY - offset, z, minimumY, maximumY);
                if (lowerLocation != null) {
                    return lowerLocation;
                }
            }
        }

        Block highestBlock = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return safeRespawnLocationAt(world, x, highestBlock.getY() + 1, z, minimumY, maximumY);
    }

    private Location safeRespawnLocationAt(World world, int x, int y, int z, int minimumY, int maximumY) {
        if (y < minimumY || y > maximumY) {
            return null;
        }

        Location location = new Location(world, x + 0.5D, y, z + 0.5D);
        return isSafeRespawnLocation(location) ? location : null;
    }

    boolean isSafeRespawnLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int y = location.getBlockY();
        if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) {
            return false;
        }

        Block feet = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), y + 1, location.getBlockZ());
        Block floor = world.getBlockAt(location.getBlockX(), y - 1, location.getBlockZ());
        return isSafeBodyBlock(feet)
                && isSafeBodyBlock(head)
                && isSafeStandingBlock(floor);
    }

    private boolean isSafeStandingBlock(Block block) {
        return block.isSolid()
                && !block.isLiquid()
                && !isHazardousBlock(block.getType())
                && block.getBoundingBox().getMaxY() <= block.getY() + 1.000001D;
    }

    private boolean isSafeBodyBlock(Block block) {
        return block.isPassable()
                && !block.isLiquid()
                && !isHazardousBlock(block.getType());
    }

    private boolean isHazardousBlock(Material material) {
        return switch (material) {
            case CACTUS,
                 CAMPFIRE,
                 END_PORTAL,
                 FIRE,
                 LAVA,
                 LAVA_CAULDRON,
                 MAGMA_BLOCK,
                 NETHER_PORTAL,
                 POWDER_SNOW,
                 SOUL_CAMPFIRE,
                 SOUL_FIRE,
                 SWEET_BERRY_BUSH,
                 WITHER_ROSE -> true;
            default -> false;
        };
    }

    boolean sameBlockLocation(Location first, Location second) {
        return first != null
                && second != null
                && first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
