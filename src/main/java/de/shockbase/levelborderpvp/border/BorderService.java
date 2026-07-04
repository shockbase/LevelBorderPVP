package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BorderService {

    private static final double BORDER_SIZE_EPSILON = 0.000001D;
    private static final double BORDER_SAFE_MARGIN_BLOCKS = 0.3D;

    private final Plugin plugin;
    private final WorldBorderApi worldBorderApi;
    private final PlayerBorderRepository playerBorderRepository;
    private final LevelBorderSettings settings;
    private final BorderSizeCalculator sizeCalculator;
    private final BorderNotifier notifier;
    private final Messages messages;
    private final Map<UUID, BukkitTask> borderAnimationTasks = new HashMap<>();
    private final Map<UUID, Double> animatedBorderSizes = new HashMap<>();
    private final Map<UUID, BukkitTask> startCountdownTasks = new HashMap<>();
    private final Set<UUID> manuallyStartedPlayers = new HashSet<>();
    private final Set<UUID> manuallyStoppedPlayers = new HashSet<>();

    public BorderService(
            Plugin plugin,
            WorldBorderApi worldBorderApi,
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            BorderNotifier notifier,
            Messages messages
    ) {
        this.plugin = plugin;
        this.worldBorderApi = worldBorderApi;
        this.playerBorderRepository = playerBorderRepository;
        this.settings = settings;
        this.sizeCalculator = sizeCalculator;
        this.notifier = notifier;
        this.messages = messages;
    }

    public void applyNextTick(Player player, BorderNotification notification) {
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player, notification));
    }

    public void applyLater(Player player, BorderNotification notification, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(player, notification), delayTicks);
    }

    public void handleLevelChange(Player player, int newLevel) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = getOrCreateBorderData(player);
        int normalizedLevel = Math.max(0, newLevel);
        boolean reachedNewHighestLevel = normalizedLevel > data.maxReachedLevel();

        if (reachedNewHighestLevel) {
            data = data.withMaxReachedLevel(normalizedLevel);
            playerBorderRepository.save(data);
        }

        if (settings.usesCurrentLevelMode()) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_CHANGED);
        } else if (reachedNewHighestLevel) {
            apply(player, data, normalizedLevel, BorderNotification.LEVEL_UP);
        }
    }

    public void handlePlayerKill(Player killer, Player killed) {
        if (settings.usesCurrentLevelMode() || !settings.highestKillBonusEnabled()) {
            return;
        }
        if (!isActive(killer)) {
            return;
        }
        if (killer.getUniqueId().equals(killed.getUniqueId())) {
            return;
        }

        PlayerBorderData killedData = updateMaxReachedLevel(killed, getOrCreateBorderData(killed));
        int bonusLevels = Math.max(0, killedData.maxReachedLevel());
        if (settings.highestKillBonusInheritsVictimBonus()) {
            bonusLevels = addLevels(bonusLevels, killedData.killBonusLevels());
        }
        if (bonusLevels <= 0) {
            return;
        }

        PlayerBorderData killerData = updateMaxReachedLevel(killer, getOrCreateBorderData(killer));
        int newKillBonusLevels = addLevels(killerData.killBonusLevels(), bonusLevels);
        if (newKillBonusLevels == killerData.killBonusLevels()) {
            return;
        }

        killerData = killerData.withKillBonusLevels(newKillBonusLevels);
        playerBorderRepository.save(killerData);
        apply(killer, killerData, Math.max(0, killer.getLevel()), BorderNotification.KILL_BONUS);
    }

    public void start(Player player, int countdownSeconds) {
        cancelStartCountdown(player);

        int boundedCountdownSeconds = Math.max(0, Math.min(countdownSeconds, settings.maxStartCountdownSeconds()));
        if (boundedCountdownSeconds <= 0) {
            activate(player, BorderNotification.JOIN);
            return;
        }

        UUID playerId = player.getUniqueId();
        BukkitRunnable countdown = new BukkitRunnable() {
            private int remainingSeconds = boundedCountdownSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    startCountdownTasks.remove(playerId);
                    cancel();
                    return;
                }

                if (remainingSeconds <= 0) {
                    startCountdownTasks.remove(playerId);
                    activate(player, BorderNotification.JOIN);
                    cancel();
                    return;
                }

                player.sendMessage(messages.text(
                        "service.countdown",
                        Messages.placeholder("seconds", remainingSeconds)
                ));
                remainingSeconds--;
            }
        };

        startCountdownTasks.put(playerId, countdown.runTaskTimer(plugin, 0L, 20L));
    }

    public void stop(Player player) {
        UUID playerId = player.getUniqueId();
        cancelStartCountdown(player);
        cancelBorderAnimation(player);
        manuallyStartedPlayers.remove(playerId);
        manuallyStoppedPlayers.add(playerId);
        worldBorderApi.resetWorldBorderToGlobal(player);
    }

    public void reset(Player player) {
        cancelStartCountdown(player);
        cancelBorderAnimation(player);

        PlayerBorderData data = createInitialBorderData(player, Math.max(0, player.getLevel()));
        playerBorderRepository.save(data);

        if (isActive(player)) {
            apply(player, data, Math.max(0, player.getLevel()), BorderNotification.JOIN);
        } else {
            worldBorderApi.resetWorldBorderToGlobal(player);
        }
    }

    public void shutdown() {
        for (BukkitTask task : startCountdownTasks.values()) {
            task.cancel();
        }
        startCountdownTasks.clear();

        for (BukkitTask task : borderAnimationTasks.values()) {
            task.cancel();
        }
        borderAnimationTasks.clear();
        animatedBorderSizes.clear();
    }

    private void apply(Player player, BorderNotification notification) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = updateMaxReachedLevel(player, getOrCreateBorderData(player));

        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    private void apply(Player player, PlayerBorderData data, int currentLevel, BorderNotification notification) {
        if (!player.isOnline()) {
            return;
        }

        double size = sizeCalculator.calculate(resolveLevelForBorder(data, currentLevel));
        double previousSize = currentDisplayedBorderSize(player, data);
        Location center = data.toLocation(player.getWorld());
        applyBorder(player, center, previousSize, size, notification);

        playerBorderRepository.save(data.withLastAppliedBorderSize(size));

        notifyPlayer(player, notification, size, previousSize);
    }

    private void applyBorder(
            Player player,
            Location center,
            double previousSize,
            double size,
            BorderNotification notification
    ) {
        if (!shouldAnimateSizeChange(notification, previousSize, size)) {
            cancelBorderAnimation(player);
            keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
            worldBorderApi.setBorder(player, size, center);
            return;
        }

        keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
        animateBorder(player, center, previousSize, size);
    }

    private double currentDisplayedBorderSize(Player player, PlayerBorderData data) {
        return animatedBorderSizes.getOrDefault(player.getUniqueId(), data.lastAppliedBorderSize());
    }

    private void animateBorder(Player player, Location center, double previousSize, double size) {
        cancelBorderAnimation(player);

        UUID playerId = player.getUniqueId();
        int totalTicks = Math.max(1, (int) Math.ceil(settings.borderTransitionMilliseconds() / 50.0D));
        animatedBorderSizes.put(playerId, previousSize);
        worldBorderApi.setBorder(player, previousSize, center);

        BukkitRunnable animation = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    borderAnimationTasks.remove(playerId);
                    animatedBorderSizes.remove(playerId);
                    cancel();
                    return;
                }

                elapsedTicks++;
                double progress = Math.min(1.0D, elapsedTicks / (double) totalTicks);
                double easedProgress = smoothStep(progress);
                double currentSize = previousSize + ((size - previousSize) * easedProgress);

                animatedBorderSizes.put(playerId, currentSize);
                worldBorderApi.setBorder(player, currentSize, center);

                if (progress >= 1.0D) {
                    borderAnimationTasks.remove(playerId);
                    animatedBorderSizes.remove(playerId);
                    worldBorderApi.setBorder(player, size, center);
                    cancel();
                }
            }
        };

        borderAnimationTasks.put(playerId, animation.runTaskTimer(plugin, 1L, 1L));
    }

    private double smoothStep(double progress) {
        return progress * progress * (3.0D - (2.0D * progress));
    }

    private void cancelBorderAnimation(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = borderAnimationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        animatedBorderSizes.remove(playerId);
    }

    private void cancelStartCountdown(Player player) {
        BukkitTask task = startCountdownTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void activate(Player player, BorderNotification notification) {
        UUID playerId = player.getUniqueId();
        manuallyStartedPlayers.add(playerId);
        manuallyStoppedPlayers.remove(playerId);
        apply(player, notification);
    }

    private boolean isActive(Player player) {
        UUID playerId = player.getUniqueId();
        if (settings.autoStartOnJoin()) {
            return !manuallyStoppedPlayers.contains(playerId);
        }
        return manuallyStartedPlayers.contains(playerId);
    }

    private void keepPlayerInsideShrinkingBorder(Player player, Location center, double previousSize, double size) {
        if (size >= previousSize - BORDER_SIZE_EPSILON) {
            return;
        }

        Location playerLocation = player.getLocation();
        double maxOffset = safeBorderOffset(size);
        double clampedX = clamp(playerLocation.getX(), center.getX() - maxOffset, center.getX() + maxOffset);
        double clampedZ = clamp(playerLocation.getZ(), center.getZ() - maxOffset, center.getZ() + maxOffset);

        if (Math.abs(clampedX - playerLocation.getX()) <= BORDER_SIZE_EPSILON
                && Math.abs(clampedZ - playerLocation.getZ()) <= BORDER_SIZE_EPSILON) {
            return;
        }

        playerLocation.setX(clampedX);
        playerLocation.setZ(clampedZ);
        player.teleport(playerLocation);
    }

    private double safeBorderOffset(double size) {
        double halfSize = size / 2.0D;
        double safeMargin = Math.min(BORDER_SAFE_MARGIN_BLOCKS, Math.max(0.0D, halfSize - 0.05D));
        return Math.max(0.0D, halfSize - safeMargin);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean shouldAnimateSizeChange(BorderNotification notification, double previousSize, double size) {
        if (settings.borderTransitionMilliseconds() <= 0L || previousSize <= 0.0D) {
            return false;
        }
        if (Math.abs(size - previousSize) <= BORDER_SIZE_EPSILON) {
            return false;
        }
        return notification == BorderNotification.LEVEL_UP
                || notification == BorderNotification.LEVEL_CHANGED
                || notification == BorderNotification.KILL_BONUS;
    }

    private void notifyPlayer(Player player, BorderNotification notification, double size, double previousSize) {
        if (notification == BorderNotification.LEVEL_UP && size > previousSize + BORDER_SIZE_EPSILON) {
            notifier.showLevelUp(player, size);
        } else if (notification == BorderNotification.LEVEL_CHANGED) {
            if (size > previousSize + BORDER_SIZE_EPSILON) {
                notifier.showLevelUp(player, size);
            } else if (size < previousSize - BORDER_SIZE_EPSILON) {
                notifier.showBorderChanged(player, size);
            }
        } else if (notification == BorderNotification.KILL_BONUS && size > previousSize + BORDER_SIZE_EPSILON) {
            notifier.showKillBonus(player, size);
        } else if (notification == BorderNotification.JOIN) {
            notifier.showJoined(player, size);
        } else if (notification == BorderNotification.RESPAWN) {
            notifier.showRespawned(player, size);
        }
    }

    private int resolveLevelForBorder(PlayerBorderData data, int currentLevel) {
        if (settings.usesCurrentLevelMode()) {
            return Math.max(0, currentLevel);
        }
        return addLevels(data.maxReachedLevel(), data.killBonusLevels());
    }

    private PlayerBorderData updateMaxReachedLevel(Player player, PlayerBorderData data) {
        int currentLevel = Math.max(0, player.getLevel());
        if (currentLevel <= data.maxReachedLevel()) {
            return data;
        }

        PlayerBorderData updated = data.withMaxReachedLevel(currentLevel);
        playerBorderRepository.save(updated);
        return updated;
    }

    private int addLevels(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private PlayerBorderData getOrCreateBorderData(Player player) {
        int currentLevel = Math.max(0, player.getLevel());
        PlayerBorderData existing = playerBorderRepository.find(
                player,
                settings.usesCurrentLevelMode(),
                sizeCalculator::calculate
        );
        if (existing != null) {
            return existing;
        }

        PlayerBorderData created = createInitialBorderData(player, currentLevel);
        playerBorderRepository.save(created);
        return created;
    }

    private PlayerBorderData createInitialBorderData(Player player, int currentLevel) {
        Block standingBlock = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        double x = standingBlock.getX();
        double y = standingBlock.getY();
        double z = standingBlock.getZ();

        if (settings.centerAtBlockCenter()) {
            x += 0.5D;
            y += 0.5D;
            z += 0.5D;
        }

        return new PlayerBorderData(
                player.getUniqueId(),
                standingBlock.getWorld().getUID(),
                standingBlock.getWorld().getName(),
                x,
                y,
                z,
                currentLevel,
                0,
                sizeCalculator.calculate(currentLevel)
        );
    }
}
