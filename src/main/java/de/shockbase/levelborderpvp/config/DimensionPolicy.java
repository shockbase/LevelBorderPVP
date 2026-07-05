package de.shockbase.levelborderpvp.config;

import org.bukkit.World;

import java.util.Locale;

public enum DimensionPolicy {
    LEGACY("legacy"),
    SAFE_PVE("safe-pve");

    private final String configValue;

    DimensionPolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public boolean usesSafePveDimensionRules() {
        return this == SAFE_PVE;
    }

    public boolean allowsPersonalBorder(World world) {
        if (this == LEGACY) {
            return true;
        }
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }

    public static DimensionPolicy fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return SAFE_PVE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DimensionPolicy policy : values()) {
            if (policy.configValue.equals(normalized)) {
                return policy;
            }
        }
        return SAFE_PVE;
    }
}
