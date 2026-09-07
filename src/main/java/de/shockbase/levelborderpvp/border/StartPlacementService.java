package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.StartPlacementMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StartPlacementService {
    private static final int START_GRID_SLOT_ATTEMPT_MULTIPLIER = 5;
    private static final int START_GRID_MIN_EXTRA_SLOT_ATTEMPTS = 16;
    private final LevelBorderSettings settings;
    private final BorderRenderer borderRenderer;
    private final SafeLocationService safeLocations;
    private record StartGridOffset(int x, int z) {}
    StartPlacementService(LevelBorderSettings settings, BorderRenderer borderRenderer, SafeLocationService safeLocations) {
        this.settings = settings;
        this.borderRenderer = borderRenderer;
        this.safeLocations = safeLocations;
    }
    boolean placeStartPlayers(List<Player> activeStartPlayers) {
        if (settings.startPlacementMode() != StartPlacementMode.GRID || activeStartPlayers.isEmpty()) {
            return true;
        }

        Map<World, List<Player>> playersByWorld = new HashMap<>();
        for (Player player : activeStartPlayers) {
            playersByWorld.computeIfAbsent(player.getWorld(), ignored -> new ArrayList<>()).add(player);
        }

        Map<Player, Location> targets = new java.util.LinkedHashMap<>();
        for (Map.Entry<World, List<Player>> entry : playersByWorld.entrySet()) {
            List<Player> players = new ArrayList<>(entry.getValue());
            List<Location> locations = findStartGridLocations(entry.getKey(), players.size());
            if (locations.size() != players.size()) return false;
            Collections.shuffle(players);
            Collections.shuffle(locations);

            for (int index = 0; index < players.size(); index++) {
                Player player = players.get(index);
                Location target = locations.get(index);
                target.setYaw(player.getLocation().getYaw());
                target.setPitch(player.getLocation().getPitch());
                targets.put(player, target);
            }
        }
        Map<Player, Location> sources = new java.util.LinkedHashMap<>();
        for (Map.Entry<Player, Location> entry : targets.entrySet()) {
            Player player = entry.getKey();
            sources.put(player, player.getLocation().clone());
            borderRenderer.resetToGlobal(player);
            if (!player.teleport(entry.getValue())
                    || !samePosition(player.getLocation(), entry.getValue())) {
                sources.forEach((movedPlayer, source) -> movedPlayer.teleport(source));
                return false;
            }
        }
        sources.forEach((player, source) -> showGridTeleportEffects(source, targets.get(player)));
        return true;
    }

    private boolean samePosition(Location actual, Location expected) {
        return actual.getWorld().equals(expected.getWorld()) && actual.distanceSquared(expected) < 0.01D;
    }

    private void showGridTeleportEffects(Location source, Location target) {
        spawnEnderTeleportEffects(source);
        spawnEnderTeleportEffects(target);
    }

    private void spawnEnderTeleportEffects(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.PORTAL, location, 64, 0.45D, 0.8D, 0.45D, 0.25D);
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.1F);
    }

    private List<Location> findStartGridLocations(World world, int playerCount) {
        List<Location> locations = new ArrayList<>();
        if (playerCount <= 0) {
            return locations;
        }

        Location spawn = world.getSpawnLocation();
        Set<String> usedColumns = new HashSet<>();
        for (StartGridOffset offset : createStartGridOffsets(playerCount)) {
            Location location = startGridLocation(world, spawn, offset, usedColumns);
            if (location == null) {
                continue;
            }

            locations.add(location);
            if (locations.size() >= playerCount) {
                break;
            }
        }
        return locations;
    }

    private Location startGridLocation(World world, Location spawn, StartGridOffset offset, Set<String> usedColumns) {
        double spacing = settings.startGridSpacingBlocks();
        int x = (int) Math.round(spawn.getX() + (offset.x() * spacing));
        int z = (int) Math.round(spawn.getZ() + (offset.z() * spacing));
        String columnKey = x + ":" + z;
        if (!usedColumns.add(columnKey)) {
            return null;
        }
        return safeLocations.findSafeRespawnLocationInColumn(world, x, z, spawn.getBlockY());
    }

    private List<StartGridOffset> createStartGridOffsets(int playerCount) {
        int targetSlotCount = playerCount + Math.max(START_GRID_MIN_EXTRA_SLOT_ATTEMPTS, playerCount * START_GRID_SLOT_ATTEMPT_MULTIPLIER);
        List<StartGridOffset> offsets = new ArrayList<>();
        Set<StartGridOffset> selectedOffsets = new HashSet<>();

        if (!settings.startGridSkipCenter()) {
            StartGridOffset center = new StartGridOffset(0, 0);
            offsets.add(center);
            selectedOffsets.add(center);
        }

        int remainingPrimarySlots = Math.max(0, playerCount - offsets.size());
        for (int ring = 1; remainingPrimarySlots > 0; ring++) {
            List<StartGridOffset> ringOffsets = ringOffsets(ring);
            List<StartGridOffset> selectedRingOffsets = selectEvenlySpacedOffsets(ringOffsets, Math.min(remainingPrimarySlots, ringOffsets.size()));
            offsets.addAll(selectedRingOffsets);
            selectedOffsets.addAll(selectedRingOffsets);
            remainingPrimarySlots -= selectedRingOffsets.size();
        }

        for (int ring = 1; offsets.size() < targetSlotCount; ring++) {
            List<StartGridOffset> fallbackRingOffsets = ringOffsets(ring);
            Collections.shuffle(fallbackRingOffsets);
            for (StartGridOffset offset : fallbackRingOffsets) {
                if (selectedOffsets.add(offset)) {
                    offsets.add(offset);
                }
                if (offsets.size() >= targetSlotCount) {
                    break;
                }
            }
        }

        return offsets;
    }

    private List<StartGridOffset> selectEvenlySpacedOffsets(List<StartGridOffset> ringOffsets, int count) {
        if (count >= ringOffsets.size()) {
            List<StartGridOffset> shuffledOffsets = new ArrayList<>(ringOffsets);
            Collections.shuffle(shuffledOffsets);
            return shuffledOffsets;
        }

        List<StartGridOffset> selectedOffsets = new ArrayList<>();
        int ringSize = ringOffsets.size();
        int startIndex = ringSize <= 1 ? 0 : (int) Math.floor(Math.random() * ringSize);
        int direction = Math.random() < 0.5D ? -1 : 1;
        for (int index = 0; index < count; index++) {
            int offsetIndex = Math.floorMod(startIndex + (direction * (int) Math.floor(index * (ringSize / (double) count))), ringSize);
            selectedOffsets.add(ringOffsets.get(offsetIndex));
        }
        Collections.shuffle(selectedOffsets);
        return selectedOffsets;
    }

    private List<StartGridOffset> ringOffsets(int ring) {
        List<StartGridOffset> offsets = new ArrayList<>();
        for (int x = -ring; x <= ring; x++) {
            offsets.add(new StartGridOffset(x, -ring));
        }
        for (int z = -ring + 1; z <= ring; z++) {
            offsets.add(new StartGridOffset(ring, z));
        }
        for (int x = ring - 1; x >= -ring; x--) {
            offsets.add(new StartGridOffset(x, ring));
        }
        for (int z = ring - 1; z > -ring; z--) {
            offsets.add(new StartGridOffset(-ring, z));
        }
        return offsets;
    }

}
