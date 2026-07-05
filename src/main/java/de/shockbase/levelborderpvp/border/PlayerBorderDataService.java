package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.data.PortalLocationData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class PlayerBorderDataService {

    private final PlayerBorderRepository playerBorderRepository;
    private final LevelBorderSettings settings;
    private final BorderSizeCalculator sizeCalculator;

    PlayerBorderDataService(
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator
    ) {
        this.playerBorderRepository = playerBorderRepository;
        this.settings = settings;
        this.sizeCalculator = sizeCalculator;
    }

    void save(PlayerBorderData data) {
        playerBorderRepository.save(data);
    }

    PlayerBorderData findExisting(Player player) {
        return playerBorderRepository.find(
                player,
                settings.usesCurrentLevelMode(),
                sizeCalculator::calculate
        );
    }

    PlayerBorderData getOrCreate(Player player) {
        int currentLevel = Math.max(0, player.getLevel());
        PlayerBorderData existing = findExisting(player);
        if (existing != null) {
            return existing;
        }

        PlayerBorderData created = createInitial(player, currentLevel);
        playerBorderRepository.save(created);
        return created;
    }

    PlayerBorderData createInitial(Player player, int currentLevel) {
        Location location = player.getLocation();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        if (settings.centerAtBlockCenter()) {
            x = location.getBlockX() + 0.5D;
            y = location.getBlockY() + 0.5D;
            z = location.getBlockZ() + 0.5D;
        }

        return new PlayerBorderData(
                player.getUniqueId(),
                location.getWorld().getUID(),
                location.getWorld().getName(),
                x,
                y,
                z,
                currentLevel,
                0,
                sizeCalculator.calculate(currentLevel),
                null
        );
    }

    boolean saveFirstOverworldPortal(Player player, Location portalLocation) {
        PlayerBorderData data = findExisting(player);
        if (data == null || data.overworldPortal() != null) {
            return false;
        }

        playerBorderRepository.save(data.withOverworldPortal(PortalLocationData.fromLocation(portalLocation)));
        return true;
    }

    PlayerBorderData updateMaxReachedLevel(Player player, PlayerBorderData data) {
        int currentLevel = Math.max(0, player.getLevel());
        if (currentLevel <= data.maxReachedLevel()) {
            return data;
        }

        PlayerBorderData updated = data.withMaxReachedLevel(currentLevel);
        playerBorderRepository.save(updated);
        return updated;
    }

    int resolveLevelForBorder(PlayerBorderData data, int currentLevel) {
        if (settings.usesCurrentLevelMode()) {
            return Math.max(0, currentLevel);
        }
        return addLevels(data.maxReachedLevel(), data.killBonusLevels());
    }

    int addLevels(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
