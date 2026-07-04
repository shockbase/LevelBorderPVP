package de.oehme.personallevelborder.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class LevelBorderSettings {

    private final FileConfiguration config;

    public LevelBorderSettings(FileConfiguration config) {
        this.config = config;
    }

    public double initialSizeBlocks() {
        return Math.max(1.0D, config.getDouble("initial-size-blocks", 3.0D));
    }

    public double growthPerLevelBlocks() {
        return Math.max(0.0D, config.getDouble("growth-per-level-blocks", 2.0D));
    }

    public double maxSizeBlocks() {
        return config.getDouble("max-size-blocks", 0.0D);
    }

    public long borderTransitionMilliseconds() {
        return Math.max(0L, config.getLong("border-transition-milliseconds", 1200L));
    }

    public boolean autoStartOnJoin() {
        return config.getBoolean("auto-start-on-join", true);
    }

    public int defaultStartCountdownSeconds() {
        return Math.max(0, config.getInt("start-countdown-seconds", 10));
    }

    public int maxStartCountdownSeconds() {
        return Math.max(defaultStartCountdownSeconds(), config.getInt("max-start-countdown-seconds", 3600));
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

    public boolean reapplyOnWorldChange() {
        return config.getBoolean("reapply-on-world-change", true);
    }

    public boolean reapplyOnRespawn() {
        return config.getBoolean("reapply-on-respawn", true);
    }
}
