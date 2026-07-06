package de.shockbase.levelborderpvp.config;

import java.util.Locale;

public enum StarterMode {
    NONE("none"),
    CHEST("chest"),
    TREE("tree"),
    BOTH("both");

    private final String configValue;

    StarterMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public boolean includesChest() {
        return this == CHEST || this == BOTH;
    }

    public boolean includesTree() {
        return this == TREE || this == BOTH;
    }

    public static StarterMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StarterMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return NONE;
    }
}
