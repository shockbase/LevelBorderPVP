package de.shockbase.levelborderpvp.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LevelBorderSettings {

    private static final List<String> DEFAULT_SCORE_TIEBREAKERS = List.of("kills", "highest-level", "deaths-ascending");

    private final JavaPlugin plugin;
    private final FileConfiguration config;

    public LevelBorderSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public double initialSizeBlocks() {
        return Math.max(1.0D, config.getDouble("initial-size-blocks", 3.0D));
    }

    public double growthPerLevelBlocks() {
        return Math.max(0.0D, config.getDouble("growth-per-level-blocks", 8.0D));
    }

    public double maxSizeBlocks() {
        return config.getDouble("max-size-blocks", 0.0D);
    }

    public String language() {
        return config.getString("language", "de");
    }

    public long borderTransitionSeconds() {
        return Math.max(0L, config.getLong("border-transition-seconds", 1L));
    }

    public double lobbyRadiusBlocks() {
        return Math.max(0.0D, config.getDouble("lobby-radius-blocks", 8.0D));
    }

    public boolean teleportPlayersToLobbySpawn() {
        return config.getBoolean("teleport-players-to-lobby-spawn", true);
    }

    public int defaultStartCountdownSeconds() {
        return Math.max(0, config.getInt("start-countdown-seconds", 10));
    }

    public int minimumStartPlayers() {
        return Math.max(1, config.getInt("minimum-start-players", 2));
    }

    public int maxStartCountdownSeconds() {
        return Math.max(defaultStartCountdownSeconds(), config.getInt("max-start-countdown-seconds", 3600));
    }

    public StartPlacementMode startPlacementMode() {
        return StartPlacementMode.fromConfig(config.getString("start-placement-mode", StartPlacementMode.GRID.configValue()));
    }

    public double startGridSpacingBlocks() {
        return Math.max(1.0D, config.getDouble("start-grid-spacing-blocks", 64.0D));
    }

    public boolean startGridSkipCenter() {
        return config.getBoolean("start-grid-skip-center", true);
    }

    public boolean resetXpOnStart() {
        return config.getBoolean("reset-xp-on-start", true);
    }

    public boolean clearInventoryOnStart() {
        return config.getBoolean("clear-inventory-on-start", true);
    }

    public StarterMode starterMode() {
        return StarterMode.fromConfig(config.getString("starter.mode", StarterMode.NONE.configValue()));
    }

    public int starterChestOffsetX() {
        return config.getInt("starter.chest.offset-x", 1);
    }

    public int starterChestOffsetZ() {
        return config.getInt("starter.chest.offset-z", 0);
    }

    public List<String> starterChestItems() {
        return config.getStringList("starter.chest.items");
    }

    public StarterTreeType starterTreeType() {
        return StarterTreeType.fromConfig(config.getString("starter.tree.type", StarterTreeType.AUTO.configValue()));
    }

    public StarterTreeType starterTreeFallbackType() {
        StarterTreeType fallbackType = StarterTreeType.fromConfig(config.getString("starter.tree.fallback-type", StarterTreeType.OAK.configValue()));
        return fallbackType == StarterTreeType.AUTO ? StarterTreeType.OAK : fallbackType;
    }

    public int starterTreeOffsetX() {
        return config.getInt("starter.tree.offset-x", -1);
    }

    public int starterTreeOffsetZ() {
        return config.getInt("starter.tree.offset-z", 0);
    }

    public int starterTreeLogs() {
        return Math.max(1, config.getInt("starter.tree.logs", 4));
    }

    public boolean starterTreeLeaves() {
        return config.getBoolean("starter.tree.leaves", true);
    }

    public boolean usesCurrentLevelMode() {
        String configuredMode = config.getString("level-mode", "highest");
        return configuredMode != null && "current".equalsIgnoreCase(configuredMode.trim());
    }

    public boolean highestKillBonusEnabled() {
        return config.getBoolean("highest-kill-bonus-enabled", false);
    }

    public boolean highestKillBonusInheritsVictimBonus() {
        return config.getBoolean("highest-kill-bonus-inherits-victim-bonus", false);
    }

    public boolean centerAtBlockCenter() {
        return config.getBoolean("center-at-block-center", true);
    }

    public DimensionPolicy dimensionPolicy() {
        return DimensionPolicy.fromConfig(config.getString("dimension-policy", DimensionPolicy.SAFE_PVE.configValue()));
    }

    public boolean reapplyOnWorldChange() {
        return config.getBoolean("reapply-on-world-change", true);
    }

    public boolean reapplyOnRespawn() {
        return config.getBoolean("reapply-on-respawn", true);
    }

    public String commandPermission() {
        String permission = config.getString("command-permission", "levelborderpvp.admin");
        if (permission == null || permission.isBlank()) {
            return "levelborderpvp.admin";
        }
        return permission.trim();
    }

    public RoundEndCondition endCondition() {
        return RoundEndCondition.fromConfig(config.getString("end-condition", RoundEndCondition.ELIMINATION.configValue()));
    }

    public int roundDurationMinutes() {
        return Math.max(1, config.getInt("round-duration-minutes", 60));
    }

    public List<String> scoreTiebreakers() {
        List<String> configured = config.getStringList("score-tiebreakers");
        return configured.isEmpty() ? DEFAULT_SCORE_TIEBREAKERS : configured;
    }

    public int winTargetLevel() {
        return Math.max(1, config.getInt("win-target-level", 30));
    }

    public double winTargetBorderSizeBlocks() {
        return Math.max(1.0D, config.getDouble("win-target-border-size-blocks", 63.0D));
    }

    public int breakoutGraceSeconds() {
        return Math.max(0, config.getInt("breakout-grace-seconds", 10));
    }

    public boolean luckPermsIntegrationEnabled() {
        return config.getBoolean("luckperms-integration-enabled", false);
    }

    public String luckPermsActiveGroup() {
        return config.getString("luckperms-active-group", "levelborder_active");
    }

    public String luckPermsSpectatorGroup() {
        return config.getString("luckperms-spectator-group", "levelborder_spectator");
    }

    public boolean luckPermsClearGroupsOnRoundEnd() {
        return config.getBoolean("luckperms-clear-groups-on-round-end", true);
    }

    public String luckPermsAddActiveCommand() {
        return config.getString("luckperms-command-add-active", "lp user {player} parent add {active_group}");
    }

    public String luckPermsRemoveActiveCommand() {
        return config.getString("luckperms-command-remove-active", "lp user {player} parent remove {active_group}");
    }

    public String luckPermsAddSpectatorCommand() {
        return config.getString("luckperms-command-add-spectator", "lp user {player} parent add {spectator_group}");
    }

    public String luckPermsRemoveSpectatorCommand() {
        return config.getString("luckperms-command-remove-spectator", "lp user {player} parent remove {spectator_group}");
    }

    public boolean rollbackIntegrationEnabled() {
        return config.getBoolean("rollback-integration-enabled", false);
    }

    public String rollbackProvider() {
        String provider = config.getString("rollback-provider", "auto");
        if (provider == null || provider.isBlank()) {
            return "auto";
        }
        return provider.trim();
    }

    public boolean rollbackOnRoundEnd() {
        return config.getBoolean("rollback-on-round-end", false);
    }

    public String coreProtectRollbackCommand() {
        return config.getString("coreprotect-rollback-command", "co rollback u:{player} t:{duration} r:#global");
    }

    public String prismRollbackCommand() {
        return config.getString("prism-rollback-command", "prism rollback player:{player} since:{duration}");
    }

    public Object configValue(String path) {
        return config.get(path);
    }

    public void setConfigValue(String path, Object value) {
        config.set(path, value);
        plugin.saveConfig();
    }
}
