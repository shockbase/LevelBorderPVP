package de.shockbase.levelborderpvp.starter;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.StarterMode;
import de.shockbase.levelborderpvp.config.StarterTreeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StarterProvisionService {

    private static final int PLACEMENT_SEARCH_RADIUS_BLOCKS = 2;
    private static final List<String> DEFAULT_CHEST_ITEMS = List.of(
            "STONE_AXE:1",
            "STONE_PICKAXE:1",
            "STONE_SHOVEL:1",
            "STONE_HOE:1",
            "STONE_SWORD:1"
    );

    private final LevelBorderSettings settings;
    private final List<PlacedStarterBlock> placedBlocks = new ArrayList<>();

    public StarterProvisionService(LevelBorderSettings settings) {
        this.settings = settings;
    }

    public void provide(Player player) {
        StarterMode mode = settings.starterMode();
        if (mode == StarterMode.NONE) {
            return;
        }

        if (mode.includesChest()) {
            placeStarterChest(player);
        }
        if (mode.includesTree()) {
            placeStarterTree(player);
        }
    }

    public void cleanupPlacedBlocks() {
        for (int index = placedBlocks.size() - 1; index >= 0; index--) {
            PlacedStarterBlock placedBlock = placedBlocks.get(index);
            Block block = placedBlock.block();
            if (block.getType() == placedBlock.placedMaterial()) {
                placedBlock.previousState().update(true, false);
            }
        }
        placedBlocks.clear();
    }

    private void placeStarterChest(Player player) {
        List<ItemStack> items = starterChestItems();
        if (items.isEmpty()) {
            return;
        }

        Block block = findPlacementBlock(player, settings.starterChestOffsetX(), settings.starterChestOffsetZ());
        if (block == null || !placeBlock(block, Material.CHEST)) {
            giveItems(player, items);
            return;
        }

        if (block.getState() instanceof Chest chest) {
            Inventory inventory = chest.getBlockInventory();
            for (ItemStack item : items) {
                inventory.addItem(item);
            }
        }
    }

    private void placeStarterTree(Player player) {
        TreeMaterials materials = resolveTreeMaterials(player);
        if (materials == null) {
            return;
        }

        Block trunkBase = findPlacementBlock(player, settings.starterTreeOffsetX(), settings.starterTreeOffsetZ());
        if (trunkBase == null) {
            player.getInventory().addItem(new ItemStack(materials.log(), settings.starterTreeLogs()));
            return;
        }

        int placedLogs = 0;
        int logs = settings.starterTreeLogs();
        for (int index = 0; index < logs; index++) {
            Block block = trunkBase.getRelative(0, index, 0);
            if (!isInsideInitialBorder(player, block) || !isReplaceable(block)) {
                break;
            }
            if (placeBlock(block, materials.log())) {
                placedLogs++;
            }
        }

        if (placedLogs <= 0) {
            player.getInventory().addItem(new ItemStack(materials.log(), logs));
            return;
        }

        if (settings.starterTreeLeaves() && materials.leaves() != null) {
            placeStarterLeaves(player, trunkBase, placedLogs, materials.leaves());
        }
    }

    private void placeStarterLeaves(Player player, Block trunkBase, int placedLogs, Material leaves) {
        int topY = placedLogs - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block block = trunkBase.getRelative(dx, topY, dz);
                if (isInsideInitialBorder(player, block) && isReplaceable(block)) {
                    placeBlock(block, leaves);
                }
            }
        }

        Block cap = trunkBase.getRelative(0, topY + 1, 0);
        if (isInsideInitialBorder(player, cap) && isReplaceable(cap)) {
            placeBlock(cap, leaves);
        }
    }

    private List<ItemStack> starterChestItems() {
        List<String> configuredItems = settings.starterChestItems();
        List<String> itemEntries = configuredItems.isEmpty() ? DEFAULT_CHEST_ITEMS : configuredItems;
        List<ItemStack> items = new ArrayList<>();
        for (String itemEntry : itemEntries) {
            ItemStack item = parseItemStack(itemEntry);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private ItemStack parseItemStack(String itemEntry) {
        if (itemEntry == null || itemEntry.isBlank()) {
            return null;
        }

        String[] parts = itemEntry.trim().split(":", 2);
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || material.isAir()) {
            return null;
        }

        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        return new ItemStack(material, amount);
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
    }

    private Block findPlacementBlock(Player player, int preferredOffsetX, int preferredOffsetZ) {
        Block preferredBlock = blockAtPlayerOffset(player, preferredOffsetX, preferredOffsetZ);
        if (canUsePlacementBlock(player, preferredBlock)) {
            return preferredBlock;
        }

        for (int radius = 1; radius <= PLACEMENT_SEARCH_RADIUS_BLOCKS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Block block = blockAtPlayerOffset(player, dx, dz);
                    if (canUsePlacementBlock(player, block)) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private Block blockAtPlayerOffset(Player player, int offsetX, int offsetZ) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return world.getBlockAt(location.getBlockX() + offsetX, location.getBlockY(), location.getBlockZ() + offsetZ);
    }

    private boolean canUsePlacementBlock(Player player, Block block) {
        return block != null
                && isInsideInitialBorder(player, block)
                && isReplaceable(block)
                && block.getRelative(0, -1, 0).isSolid()
                && !sameBlock(player.getLocation(), block);
    }

    private boolean placeBlock(Block block, Material material) {
        if (material == null || material.isAir()) {
            return false;
        }

        BlockState previousState = block.getState();
        block.setType(material, false);
        placedBlocks.add(new PlacedStarterBlock(block, previousState, material));
        return true;
    }

    private boolean isInsideInitialBorder(Player player, Block block) {
        Location playerLocation = player.getLocation();
        if (playerLocation.getWorld() == null || !playerLocation.getWorld().equals(block.getWorld())) {
            return false;
        }

        double centerX = settings.centerAtBlockCenter() ? playerLocation.getBlockX() + 0.5D : playerLocation.getX();
        double centerZ = settings.centerAtBlockCenter() ? playerLocation.getBlockZ() + 0.5D : playerLocation.getZ();
        double blockCenterX = block.getX() + 0.5D;
        double blockCenterZ = block.getZ() + 0.5D;
        double radius = Math.max(0.5D, settings.initialSizeBlocks() / 2.0D);

        return Math.abs(blockCenterX - centerX) <= radius
                && Math.abs(blockCenterZ - centerZ) <= radius;
    }

    private boolean sameBlock(Location location, Block block) {
        return location.getWorld() != null
                && location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private boolean isReplaceable(Block block) {
        Material material = block.getType();
        if (material.isAir()) {
            return true;
        }

        return switch (material) {
            case SHORT_GRASS,
                 TALL_GRASS,
                 FERN,
                 LARGE_FERN,
                 DEAD_BUSH,
                 VINE,
                 GLOW_LICHEN,
                 SNOW -> true;
            default -> false;
        };
    }

    private TreeMaterials resolveTreeMaterials(Player player) {
        StarterTreeType type = settings.starterTreeType();
        if (type == StarterTreeType.AUTO) {
            Block block = blockAtPlayerOffset(player, settings.starterTreeOffsetX(), settings.starterTreeOffsetZ());
            type = resolveTreeTypeFromBiome(block, settings.starterTreeFallbackType());
        }
        return treeMaterials(type, settings.starterTreeFallbackType());
    }

    private StarterTreeType resolveTreeTypeFromBiome(Block block, StarterTreeType fallbackType) {
        if (block == null) {
            return fallbackType;
        }

        String biome = String.valueOf(block.getBiome()).toUpperCase(Locale.ROOT);
        if (biome.contains("CHERRY")) {
            return StarterTreeType.CHERRY;
        }
        if (biome.contains("MANGROVE")) {
            return StarterTreeType.MANGROVE;
        }
        if (biome.contains("DARK_FOREST")) {
            return StarterTreeType.DARK_OAK;
        }
        if (biome.contains("JUNGLE") || biome.contains("BAMBOO")) {
            return StarterTreeType.JUNGLE;
        }
        if (biome.contains("SAVANNA")) {
            return StarterTreeType.ACACIA;
        }
        if (biome.contains("BIRCH")) {
            return StarterTreeType.BIRCH;
        }
        if (biome.contains("TAIGA") || biome.contains("SNOWY") || biome.contains("GROVE")) {
            return StarterTreeType.SPRUCE;
        }
        return fallbackType;
    }

    private TreeMaterials treeMaterials(StarterTreeType type, StarterTreeType fallbackType) {
        StarterTreeType resolvedType = type == StarterTreeType.AUTO ? fallbackType : type;
        Material log = material(resolvedType.materialPrefix() + "_LOG");
        Material leaves = material(resolvedType.materialPrefix() + "_LEAVES");
        if (log == null) {
            log = material(fallbackType.materialPrefix() + "_LOG");
            leaves = material(fallbackType.materialPrefix() + "_LEAVES");
        }
        return log == null ? null : new TreeMaterials(log, leaves);
    }

    private Material material(String name) {
        return name == null ? null : Material.matchMaterial(name);
    }

    private record TreeMaterials(Material log, Material leaves) {
    }

    private record PlacedStarterBlock(Block block, BlockState previousState, Material placedMaterial) {
    }
}
