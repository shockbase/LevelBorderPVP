package de.shockbase.levelborderpvp.config;

import java.util.Locale;

public enum RoundEndCondition {
    DISABLED("disabled"),
    TIMED_SCORE("timed-score"),
    TARGET_LEVEL("target-level"),
    TARGET_BORDER("target-border"),
    ELIMINATION("elimination");

    private final String configValue;

    RoundEndCondition(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static RoundEndCondition fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return TIMED_SCORE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RoundEndCondition condition : values()) {
            if (condition.configValue.equals(normalized)) {
                return condition;
            }
        }
        return TIMED_SCORE;
    }
}
