package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Calculates personal border bounds independently of rendering and round outcomes. */
final class PersonalBorderGeometry {
    private final LevelBorderSettings settings;
    private final RoundPlayerTracker roundPlayers;
    private final RoundSession session;
    private final PlayerBorderDataService playerBorderDataService;
    private final BorderSizeCalculator sizeCalculator;

    PersonalBorderGeometry(
            LevelBorderSettings settings,
            RoundPlayerTracker roundPlayers,
            RoundSession session,
            PlayerBorderDataService playerBorderDataService,
            BorderSizeCalculator sizeCalculator
    ) {
        this.settings = settings;
        this.roundPlayers = roundPlayers;
        this.session = session;
        this.playerBorderDataService = playerBorderDataService;
        this.sizeCalculator = sizeCalculator;
    }

    boolean isInsidePersonalBorder(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null || !isActive(player)) {
            return false;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !isSameWorld(data, location.getWorld())) {
            return false;
        }

        double borderSize = borderSize(data, Math.max(0, player.getLevel()));
        double maxOffset = Math.max(0.0D, borderSize / 2.0D);
        return Math.abs(location.getX() - data.x()) <= maxOffset
                && Math.abs(location.getZ() - data.z()) <= maxOffset;
    }

    boolean isOutsideCurrentPersonalBorder(Player player, Location location) {
        if (player == null
                || location == null
                || location.getWorld() == null
                || !isActive(player)
                || !settings.dimensionPolicy().allowsPersonalBorder(location.getWorld())) {
            return false;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !isSameWorld(data, location.getWorld())) {
            return false;
        }

        double borderSize = borderSize(data, Math.max(0, player.getLevel()));
        double maxOffset = Math.max(0.0D, borderSize / 2.0D);
        return Math.abs(location.getX() - data.x()) > maxOffset
                || Math.abs(location.getZ() - data.z()) > maxOffset;
    }

    double borderSize(PlayerBorderData data, int currentLevel) {
        int borderLevel = playerBorderDataService.resolveLevelForBorder(data, currentLevel);
        return sizeCalculator.calculate(borderLevel);
    }

    boolean isSameWorld(PlayerBorderData data, World world) {
        if (data.worldId().equals(world.getUID())) {
            return true;
        }
        return data.worldName() != null && data.worldName().equals(world.getName());
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
