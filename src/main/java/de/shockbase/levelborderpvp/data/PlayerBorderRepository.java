package de.shockbase.levelborderpvp.data;

import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerBorderRepository {

    private final File dataFile;
    private final Logger logger;
    private final Messages messages;

    private YamlConfiguration playerData;

    public PlayerBorderRepository(File dataFolder, Logger logger, Messages messages) {
        this.dataFile = new File(dataFolder, "players.yml");
        this.logger = logger;
        this.messages = messages;
    }

    public void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
        }
        playerData = YamlConfiguration.loadConfiguration(dataFile);
    }

    public PlayerBorderData find(
            Player player,
            boolean usesCurrentLevelMode,
            IntToDoubleFunction fallbackSizeCalculator
    ) {
        String basePath = basePath(player.getUniqueId());
        if (!playerData.contains(basePath + ".center.x")) {
            return null;
        }

        int maxReachedLevel = Math.max(0, playerData.getInt(basePath + ".max-reached-level", player.getLevel()));
        int killBonusLevels = Math.max(0, playerData.getInt(basePath + ".kill-bonus-levels", 0));
        int borderLevel = usesCurrentLevelMode ? Math.max(0, player.getLevel()) : addLevels(maxReachedLevel, killBonusLevels);
        double fallbackLastAppliedBorderSize = fallbackSizeCalculator.applyAsDouble(borderLevel);

        return new PlayerBorderData(
                player.getUniqueId(),
                parseUuid(playerData.getString(basePath + ".center.world-id"), player.getWorld().getUID()),
                playerData.getString(basePath + ".center.world-name", player.getWorld().getName()),
                playerData.getDouble(basePath + ".center.x"),
                playerData.getDouble(basePath + ".center.y"),
                playerData.getDouble(basePath + ".center.z"),
                maxReachedLevel,
                killBonusLevels,
                Math.max(0.0D, playerData.getDouble(basePath + ".last-applied-size", fallbackLastAppliedBorderSize)),
                parsePortal(basePath + ".overworld-portal")
        );
    }

    public void save(PlayerBorderData data) {
        String basePath = basePath(data.playerId());
        playerData.set(basePath + ".center.world-id", data.worldId().toString());
        playerData.set(basePath + ".center.world-name", data.worldName());
        playerData.set(basePath + ".center.x", data.x());
        playerData.set(basePath + ".center.y", data.y());
        playerData.set(basePath + ".center.z", data.z());
        playerData.set(basePath + ".max-reached-level", data.maxReachedLevel());
        playerData.set(basePath + ".kill-bonus-levels", data.killBonusLevels());
        playerData.set(basePath + ".last-applied-size", data.lastAppliedBorderSize());
        savePortal(basePath + ".overworld-portal", data.overworldPortal());
        save();
    }

    public void save() {
        if (playerData == null) {
            return;
        }

        try {
            playerData.save(dataFile);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, messages.text("log.players-save-failed"), exception);
        }
    }

    private String basePath(UUID playerId) {
        return "players." + playerId;
    }

    private int addLevels(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private UUID parseUuid(String value, UUID fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private PortalLocationData parsePortal(String path) {
        if (!playerData.contains(path + ".x")) {
            return null;
        }

        UUID worldId = parseUuid(playerData.getString(path + ".world-id"), null);
        String worldName = playerData.getString(path + ".world-name");
        if (worldId == null && (worldName == null || worldName.isBlank())) {
            return null;
        }

        return new PortalLocationData(
                worldId,
                worldName,
                playerData.getDouble(path + ".x"),
                playerData.getDouble(path + ".y"),
                playerData.getDouble(path + ".z"),
                (float) playerData.getDouble(path + ".yaw", 0.0D),
                (float) playerData.getDouble(path + ".pitch", 0.0D)
        );
    }

    private void savePortal(String path, PortalLocationData portal) {
        if (portal == null) {
            playerData.set(path, null);
            return;
        }

        playerData.set(path + ".world-id", portal.worldId() == null ? null : portal.worldId().toString());
        playerData.set(path + ".world-name", portal.worldName());
        playerData.set(path + ".x", portal.x());
        playerData.set(path + ".y", portal.y());
        playerData.set(path + ".z", portal.z());
        playerData.set(path + ".yaw", portal.yaw());
        playerData.set(path + ".pitch", portal.pitch());
    }
}
