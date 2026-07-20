package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class OtherPlayerBorderRenderer {

    private static final long UPDATE_INTERVAL_TICKS = 10L;
    private static final double VIEW_DISTANCE_BLOCKS = 64.0D;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE_BLOCKS * VIEW_DISTANCE_BLOCKS;
    private static final double PARTICLE_SPACING_BLOCKS = 2.0D;
    private static final double[] HEIGHT_OFFSETS = {-2.0D, 0.0D, 2.0D};
    private static final int MAX_PARTICLES_PER_VIEWER = 384;
    private static final double GEOMETRY_EPSILON = 0.000001D;

    private static final Particle.DustTransition NORMAL_TRANSITION = new Particle.DustTransition(
            Color.fromRGB(0, 80, 255),
            Color.fromRGB(0, 255, 255),
            1.0F
    );
    private static final Particle.DustTransition OVERLAP_TRANSITION = new Particle.DustTransition(
            Color.fromRGB(255, 140, 0),
            Color.fromRGB(255, 0, 0),
            1.0F
    );

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final PlayerBorderDataService playerBorderDataService;
    private final BorderSizeCalculator sizeCalculator;
    private final RoundPlayerTracker roundPlayers;

    private BukkitTask renderTask;

    OtherPlayerBorderRenderer(
            Plugin plugin,
            LevelBorderSettings settings,
            PlayerBorderDataService playerBorderDataService,
            BorderSizeCalculator sizeCalculator,
            RoundPlayerTracker roundPlayers
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.playerBorderDataService = playerBorderDataService;
        this.sizeCalculator = sizeCalculator;
        this.roundPlayers = roundPlayers;
    }

    void refresh(boolean roundActive) {
        if (roundActive && settings.showOtherPlayerBorders()) {
            start();
        } else {
            stop();
        }
    }

    void start() {
        if (renderTask != null || !settings.showOtherPlayerBorders()) {
            return;
        }
        renderTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::render,
                0L,
                UPDATE_INTERVAL_TICKS
        );
    }

    void stop() {
        if (renderTask == null) {
            return;
        }
        renderTask.cancel();
        renderTask = null;
    }

    private void render() {
        if (!settings.showOtherPlayerBorders()) {
            stop();
            return;
        }

        List<BorderSnapshot> activeBorders = activeBorderSnapshots();
        if (activeBorders.isEmpty()) {
            return;
        }

        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!isViewer(viewer)) {
                continue;
            }
            renderFor(viewer, activeBorders);
        }
    }

    private List<BorderSnapshot> activeBorderSnapshots() {
        List<BorderSnapshot> snapshots = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!roundPlayers.isActive(RoundState.ACTIVE, player)
                    || !settings.dimensionPolicy().allowsPersonalBorder(player.getWorld())) {
                continue;
            }

            BorderSnapshot snapshot = snapshot(player);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    private BorderSnapshot snapshot(Player player) {
        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null) {
            return null;
        }

        int level = playerBorderDataService.resolveLevelForBorder(data, Math.max(0, player.getLevel()));
        double size = sizeCalculator.calculate(level);
        return new BorderSnapshot(
                player.getUniqueId(),
                player.getWorld(),
                data.x(),
                data.z(),
                size
        );
    }

    private boolean isViewer(Player player) {
        return roundPlayers.isActive(RoundState.ACTIVE, player) || roundPlayers.isSpectator(player);
    }

    private void renderFor(Player viewer, List<BorderSnapshot> activeBorders) {
        Location viewerLocation = viewer.getLocation();
        World viewerWorld = viewerLocation.getWorld();
        if (viewerWorld == null) {
            return;
        }

        BorderSnapshot ownBorder = ownBorder(viewer, activeBorders);
        List<VisibleBorder> visibleBorders = new ArrayList<>();
        for (BorderSnapshot border : activeBorders) {
            if (border.playerId().equals(viewer.getUniqueId()) || !border.world().equals(viewerWorld)) {
                continue;
            }

            List<BorderPoint> points = visiblePoints(border, viewerLocation.getX(), viewerLocation.getZ());
            if (!points.isEmpty()) {
                visibleBorders.add(new VisibleBorder(border, points));
            }
        }

        int remainingParticles = MAX_PARTICLES_PER_VIEWER;
        for (int index = 0; index < visibleBorders.size() && remainingParticles >= HEIGHT_OFFSETS.length; index++) {
            VisibleBorder visibleBorder = visibleBorders.get(index);
            int remainingBorders = visibleBorders.size() - index;
            int budget = Math.max(
                    HEIGHT_OFFSETS.length,
                    remainingParticles / remainingBorders
            );
            int spawned = spawnBorder(
                    viewer,
                    viewerLocation,
                    visibleBorder,
                    overlaps(ownBorder, visibleBorder.border()) ? OVERLAP_TRANSITION : NORMAL_TRANSITION,
                    budget
            );
            remainingParticles -= spawned;
        }
    }

    private BorderSnapshot ownBorder(Player viewer, List<BorderSnapshot> activeBorders) {
        if (!roundPlayers.isActive(RoundState.ACTIVE, viewer)) {
            return null;
        }
        UUID viewerId = viewer.getUniqueId();
        for (BorderSnapshot border : activeBorders) {
            if (border.playerId().equals(viewerId)) {
                return border;
            }
        }
        return null;
    }

    private int spawnBorder(
            Player viewer,
            Location viewerLocation,
            VisibleBorder visibleBorder,
            Particle.DustTransition transition,
            int particleBudget
    ) {
        List<BorderPoint> points = visibleBorder.points();
        int pointBudget = Math.min(points.size(), particleBudget / HEIGHT_OFFSETS.length);
        if (pointBudget <= 0) {
            return 0;
        }

        double viewerY = viewerLocation.getY();
        for (int index = 0; index < pointBudget; index++) {
            int pointIndex = (int) Math.floor(((index + 0.5D) * points.size()) / pointBudget);
            BorderPoint point = points.get(Math.min(pointIndex, points.size() - 1));
            for (double heightOffset : HEIGHT_OFFSETS) {
                viewer.spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        point.x(),
                        viewerY + heightOffset,
                        point.z(),
                        1,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        transition
                );
            }
        }
        return pointBudget * HEIGHT_OFFSETS.length;
    }

    private List<BorderPoint> visiblePoints(BorderSnapshot border, double viewerX, double viewerZ) {
        double halfSize = border.size() / 2.0D;
        double minX = border.centerX() - halfSize;
        double maxX = border.centerX() + halfSize;
        double minZ = border.centerZ() - halfSize;
        double maxZ = border.centerZ() + halfSize;

        List<BorderPoint> points = new ArrayList<>();
        addVerticalEdge(points, minX, minZ, maxZ, viewerX, viewerZ);
        addVerticalEdge(points, maxX, minZ, maxZ, viewerX, viewerZ);
        addHorizontalEdge(points, minZ, minX, maxX, viewerX, viewerZ);
        addHorizontalEdge(points, maxZ, minX, maxX, viewerX, viewerZ);
        return points;
    }

    private void addVerticalEdge(
            List<BorderPoint> points,
            double x,
            double edgeMinZ,
            double edgeMaxZ,
            double viewerX,
            double viewerZ
    ) {
        double xDistance = x - viewerX;
        double remainingDistanceSquared = VIEW_DISTANCE_SQUARED - (xDistance * xDistance);
        if (remainingDistanceSquared < -GEOMETRY_EPSILON) {
            return;
        }

        double visibleRadius = Math.sqrt(Math.max(0.0D, remainingDistanceSquared));
        double visibleMin = Math.max(edgeMinZ, viewerZ - visibleRadius);
        double visibleMax = Math.min(edgeMaxZ, viewerZ + visibleRadius);
        addSamples(points, visibleMin, visibleMax, edgeMinZ, value -> new BorderPoint(x, value));
    }

    private void addHorizontalEdge(
            List<BorderPoint> points,
            double z,
            double edgeMinX,
            double edgeMaxX,
            double viewerX,
            double viewerZ
    ) {
        double zDistance = z - viewerZ;
        double remainingDistanceSquared = VIEW_DISTANCE_SQUARED - (zDistance * zDistance);
        if (remainingDistanceSquared < -GEOMETRY_EPSILON) {
            return;
        }

        double visibleRadius = Math.sqrt(Math.max(0.0D, remainingDistanceSquared));
        double visibleMin = Math.max(edgeMinX, viewerX - visibleRadius);
        double visibleMax = Math.min(edgeMaxX, viewerX + visibleRadius);
        addSamples(points, visibleMin, visibleMax, edgeMinX, value -> new BorderPoint(value, z));
    }

    private void addSamples(
            List<BorderPoint> points,
            double visibleMin,
            double visibleMax,
            double edgeStart,
            PointFactory pointFactory
    ) {
        if (visibleMin > visibleMax + GEOMETRY_EPSILON) {
            return;
        }

        double firstSample = edgeStart + Math.ceil(
                (visibleMin - edgeStart) / PARTICLE_SPACING_BLOCKS
        ) * PARTICLE_SPACING_BLOCKS;
        if (firstSample > visibleMax + GEOMETRY_EPSILON) {
            addPointIfMissing(points, pointFactory.create((visibleMin + visibleMax) / 2.0D));
            return;
        }

        for (double value = firstSample; value <= visibleMax + GEOMETRY_EPSILON; value += PARTICLE_SPACING_BLOCKS) {
            addPointIfMissing(points, pointFactory.create(value));
        }
    }

    private void addPointIfMissing(List<BorderPoint> points, BorderPoint point) {
        if (!points.contains(point)) {
            points.add(point);
        }
    }

    private boolean overlaps(BorderSnapshot first, BorderSnapshot second) {
        if (first == null || !first.world().equals(second.world())) {
            return false;
        }

        double combinedHalfSizes = (first.size() + second.size()) / 2.0D;
        return Math.abs(first.centerX() - second.centerX()) <= combinedHalfSizes + GEOMETRY_EPSILON
                && Math.abs(first.centerZ() - second.centerZ()) <= combinedHalfSizes + GEOMETRY_EPSILON;
    }

    @FunctionalInterface
    private interface PointFactory {
        BorderPoint create(double value);
    }

    private record BorderSnapshot(UUID playerId, World world, double centerX, double centerZ, double size) {
    }

    private record VisibleBorder(BorderSnapshot border, List<BorderPoint> points) {
    }

    private record BorderPoint(double x, double z) {
    }
}
