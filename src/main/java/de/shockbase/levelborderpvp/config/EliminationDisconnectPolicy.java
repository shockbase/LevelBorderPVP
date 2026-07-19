package de.shockbase.levelborderpvp.config;

import java.util.Locale;

public enum EliminationDisconnectPolicy {

    ELIMINATE("eliminate"),
    GRACE_PERIOD("grace-period");

    private final String configValue;

    EliminationDisconnectPolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static EliminationDisconnectPolicy fromConfig(String value) {
        if (value == null) {
            return ELIMINATE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EliminationDisconnectPolicy policy : values()) {
            if (policy.configValue.equals(normalized)) {
                return policy;
            }
        }
        return ELIMINATE;
    }
}
