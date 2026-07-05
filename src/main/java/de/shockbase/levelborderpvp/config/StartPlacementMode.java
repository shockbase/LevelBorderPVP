package de.shockbase.levelborderpvp.config;

import java.util.Locale;

public enum StartPlacementMode {
    SPREAD("spread"),
    GRID("grid");

    private final String configValue;

    StartPlacementMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static StartPlacementMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return SPREAD;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StartPlacementMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return SPREAD;
    }
}
