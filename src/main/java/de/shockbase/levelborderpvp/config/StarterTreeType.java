package de.shockbase.levelborderpvp.config;

import java.util.Locale;

public enum StarterTreeType {
    AUTO("auto", null),
    OAK("oak", "OAK"),
    SPRUCE("spruce", "SPRUCE"),
    BIRCH("birch", "BIRCH"),
    JUNGLE("jungle", "JUNGLE"),
    ACACIA("acacia", "ACACIA"),
    DARK_OAK("dark_oak", "DARK_OAK"),
    MANGROVE("mangrove", "MANGROVE"),
    CHERRY("cherry", "CHERRY");

    private final String configValue;
    private final String materialPrefix;

    StarterTreeType(String configValue, String materialPrefix) {
        this.configValue = configValue;
        this.materialPrefix = materialPrefix;
    }

    public String configValue() {
        return configValue;
    }

    public String materialPrefix() {
        return materialPrefix;
    }

    public static StarterTreeType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (StarterTreeType type : values()) {
            if (type.configValue.equals(normalized)) {
                return type;
            }
        }
        return AUTO;
    }
}
