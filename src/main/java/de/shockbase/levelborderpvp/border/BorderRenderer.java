package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.IWorldBorder;
import com.github.yannicklamprecht.worldborder.api.WorldBorderAction;
import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class BorderRenderer {

    private static final double BORDER_SIZE_EPSILON = 0.000001D;
    private static final double BORDER_SAFE_MARGIN_BLOCKS = 0.3D;

    private final WorldBorderApi worldBorderApi;
    private final PlayerBorderRepository playerBorderRepository;
    private final LevelBorderSettings settings;
    private final BorderSizeCalculator sizeCalculator;
    private final BorderNotifier notifier;

    BorderRenderer(
            WorldBorderApi worldBorderApi,
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            BorderNotifier notifier
    ) {
        this.worldBorderApi = worldBorderApi;
        this.playerBorderRepository = playerBorderRepository;
        this.settings = settings;
        this.sizeCalculator = sizeCalculator;
        this.notifier = notifier;
    }

    double apply(Player player, PlayerBorderData data, int borderLevel, BorderNotification notification) {
        if (!player.isOnline()) {
            return Double.NaN;
        }

        double size = sizeCalculator.calculate(borderLevel);
        double previousSize = currentDisplayedBorderSize(data);
        Location center = data.toLocation(player.getWorld());
        applyBorder(player, center, previousSize, size, notification);

        playerBorderRepository.save(data.withLastAppliedBorderSize(size));
        notifyPlayer(player, notification, size, previousSize);
        return size;
    }

    void applyLobbyBorder(Player player) {
        double lobbyRadius = settings.lobbyRadiusBlocks();
        if (lobbyRadius <= 0.0D) {
            resetToGlobal(player);
            return;
        }

        worldBorderApi.setBorder(player, lobbyRadius * 2.0D, player.getWorld().getSpawnLocation());
    }

    void applySpectator(Player player, BorderNotification notification) {
        resetToGlobal(player);

        if (notification == BorderNotification.SPECTATOR
                || notification == BorderNotification.JOIN
                || notification == BorderNotification.RESPAWN) {
            notifier.showSpectator(player);
        }
    }

    void resetToGlobal(Player player) {
        worldBorderApi.resetWorldBorderToGlobal(player);
    }

    private void applyBorder(
            Player player,
            Location center,
            double previousSize,
            double size,
            BorderNotification notification
    ) {
        if (!shouldAnimateSizeChange(notification, previousSize, size)) {
            keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
            worldBorderApi.setBorder(player, size, center);
            return;
        }

        keepPlayerInsideShrinkingBorder(player, center, previousSize, size);
        worldBorderApi.setBorder(player, previousSize, center);
        IWorldBorder border = worldBorderApi.getWorldBorder(player);
        border.lerp(previousSize, size, settings.borderTransitionSeconds());
        border.send(player, WorldBorderAction.LERP_SIZE);
    }

    private double currentDisplayedBorderSize(PlayerBorderData data) {
        return data.lastAppliedBorderSize();
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
        if (settings.borderTransitionSeconds() <= 0L || previousSize <= 0.0D) {
            return false;
        }
        if (Math.abs(size - previousSize) <= BORDER_SIZE_EPSILON) {
            return false;
        }
        return notification == BorderNotification.LEVEL_UP
                || notification == BorderNotification.LEVEL_CHANGED
                || notification == BorderNotification.PLAYER_KILL;
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
        } else if (notification == BorderNotification.JOIN) {
            notifier.showJoined(player, size);
        } else if (notification == BorderNotification.RESPAWN) {
            notifier.showRespawned(player, size);
        }
    }
}
