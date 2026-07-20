package de.shockbase.levelborderpvp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class ConfigSchema {

    private static final Map<String, Option> OPTIONS = createOptions();

    private ConfigSchema() {
    }

    public static List<Option> options() {
        return List.copyOf(OPTIONS.values());
    }

    public static List<Option> options(Category category) {
        List<Option> matching = new ArrayList<>();
        for (Option option : OPTIONS.values()) {
            if (option.category() == category) {
                matching.add(option);
            }
        }
        return List.copyOf(matching);
    }

    public static Option find(String path) {
        if (path == null) {
            return null;
        }
        return OPTIONS.get(path.toLowerCase(Locale.ROOT));
    }

    public static Object parse(Option option, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw ValidationException.of("empty");
        }

        return switch (option.type()) {
            case BOOLEAN -> parseBoolean(value);
            case INT -> validateNumber(option, parseInteger(value));
            case LONG -> validateNumber(option, parseLong(value));
            case DOUBLE -> validateNumber(option, parseDouble(value));
            case STRING -> parseString(option, value);
            case STRING_LIST -> parseStringList(value);
        };
    }

    public static Object parseNumber(Option option, Number number) {
        if (number == null) {
            throw ValidationException.of("missing");
        }

        return switch (option.type()) {
            case INT -> validateNumber(option, Math.round(number.floatValue()));
            case LONG -> validateNumber(option, Math.round(number.doubleValue()));
            case DOUBLE -> validateNumber(option, number.doubleValue());
            default -> throw new IllegalArgumentException("Option is not numeric: " + option.path());
        };
    }

    public static String format(Object value) {
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(",");
            for (Object entry : list) {
                joiner.add(String.valueOf(entry));
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    public static String formatForInput(Option option, Object value) {
        if (value == null) {
            return "";
        }
        if (option.type() == ValueType.STRING_LIST && value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner("\n");
            for (Object entry : list) {
                joiner.add(String.valueOf(entry));
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    private static Map<String, Option> createOptions() {
        Map<String, Option> options = new LinkedHashMap<>();

        addNumber(options, "initial-size-blocks", ValueType.DOUBLE, Category.BORDER, 1.0D, 59_999_968.0D, 1.0D);
        addNumber(options, "growth-per-level-blocks", ValueType.DOUBLE, Category.BORDER, 0.0D, 59_999_968.0D, 1.0D);
        addEnum(options, "level-mode", Category.BORDER, "highest", "current");
        add(options, "highest-kill-bonus-enabled", ValueType.BOOLEAN, Category.BORDER);
        add(options, "highest-kill-bonus-inherits-victim-bonus", ValueType.BOOLEAN, Category.BORDER);
        add(options, "advancement-bonus-enabled", ValueType.BOOLEAN, Category.BORDER);
        addNumber(options, "advancement-bonus-levels", ValueType.INT, Category.BORDER, 0.0D, 1_000.0D, 1.0D);
        add(options, "advancement-excluded-prefixes", ValueType.STRING_LIST, Category.BORDER);
        add(options, "center-at-block-center", ValueType.BOOLEAN, Category.BORDER);
        addNumber(options, "max-size-blocks", ValueType.DOUBLE, Category.BORDER, null, 59_999_968.0D, 1.0D);
        addNumber(options, "border-transition-seconds", ValueType.LONG, Category.BORDER, 0.0D, 3_600.0D, 1.0D);
        add(options, "show-other-player-borders", ValueType.BOOLEAN, Category.BORDER);

        addEnum(options, "end-condition", Category.ROUND, "timed-score", "target-level", "target-border", "elimination", "disabled");
        addNumber(options, "round-duration-minutes", ValueType.INT, Category.ROUND, 1.0D, 10_080.0D, 1.0D);
        add(options, "score-tiebreakers", ValueType.STRING_LIST, Category.ROUND);
        addNumber(options, "win-target-level", ValueType.INT, Category.ROUND, 1.0D, 10_000.0D, 1.0D);
        addNumber(options, "win-target-border-size-blocks", ValueType.DOUBLE, Category.ROUND, 1.0D, 59_999_968.0D, 1.0D);
        addEnum(options, "elimination-disconnect-policy", Category.ROUND, "eliminate", "grace-period");
        addNumber(options, "elimination-reconnect-grace-seconds", ValueType.INT, Category.ROUND, 0.0D, 86_400.0D, 1.0D);

        addNumber(options, "lobby-radius-blocks", ValueType.DOUBLE, Category.START_LOBBY, 0.0D, 10_000.0D, 1.0D);
        add(options, "teleport-players-to-lobby-spawn", ValueType.BOOLEAN, Category.START_LOBBY);
        addNumber(options, "start-countdown-seconds", ValueType.INT, Category.START_LOBBY, 0.0D, 3_600.0D, 1.0D);
        addNumber(options, "minimum-start-players", ValueType.INT, Category.START_LOBBY, 1.0D, 1_000.0D, 1.0D);
        addNumber(options, "max-start-countdown-seconds", ValueType.INT, Category.START_LOBBY, 0.0D, 86_400.0D, 5.0D);
        addEnum(options, "start-placement-mode", Category.START_LOBBY, "spread", "grid");
        addNumber(options, "start-grid-spacing-blocks", ValueType.DOUBLE, Category.START_LOBBY, 1.0D, 10_000.0D, 1.0D);
        add(options, "start-grid-skip-center", ValueType.BOOLEAN, Category.START_LOBBY);
        add(options, "reset-xp-on-start", ValueType.BOOLEAN, Category.START_LOBBY);
        add(options, "clear-inventory-on-start", ValueType.BOOLEAN, Category.START_LOBBY);
        add(options, "language", ValueType.STRING, Category.START_LOBBY);

        addEnum(options, "starter.mode", Category.STARTER, "none", "chest", "tree", "both");
        addNumber(options, "starter.chest.offset-x", ValueType.INT, Category.STARTER, -1_024.0D, 1_024.0D, 1.0D);
        addNumber(options, "starter.chest.offset-z", ValueType.INT, Category.STARTER, -1_024.0D, 1_024.0D, 1.0D);
        add(options, "starter.chest.items", ValueType.STRING_LIST, Category.STARTER);
        addEnum(options, "starter.tree.type", Category.STARTER, "auto", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
        addEnum(options, "starter.tree.fallback-type", Category.STARTER, "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
        addNumber(options, "starter.tree.offset-x", ValueType.INT, Category.STARTER, -1_024.0D, 1_024.0D, 1.0D);
        addNumber(options, "starter.tree.offset-z", ValueType.INT, Category.STARTER, -1_024.0D, 1_024.0D, 1.0D);
        addNumber(options, "starter.tree.logs", ValueType.INT, Category.STARTER, 1.0D, 64.0D, 1.0D);
        add(options, "starter.tree.leaves", ValueType.BOOLEAN, Category.STARTER);

        addEnum(options, "dimension-policy", Category.SPECTATORS_DIMENSIONS, "safe-pve", "legacy");
        add(options, "reapply-on-world-change", ValueType.BOOLEAN, Category.SPECTATORS_DIMENSIONS);
        add(options, "reapply-on-respawn", ValueType.BOOLEAN, Category.SPECTATORS_DIMENSIONS);
        addNumber(options, "breakout-grace-seconds", ValueType.INT, Category.SPECTATORS_DIMENSIONS, 0.0D, 3_600.0D, 1.0D);

        add(options, "command-permission", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-integration-enabled", ValueType.BOOLEAN, Category.LUCKPERMS);
        add(options, "luckperms-active-group", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-spectator-group", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-clear-groups-on-round-end", ValueType.BOOLEAN, Category.LUCKPERMS);
        add(options, "luckperms-command-add-active", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-command-remove-active", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-command-add-spectator", ValueType.STRING, Category.LUCKPERMS);
        add(options, "luckperms-command-remove-spectator", ValueType.STRING, Category.LUCKPERMS);

        add(options, "rollback-integration-enabled", ValueType.BOOLEAN, Category.ROLLBACK);
        addEnum(options, "rollback-provider", Category.ROLLBACK, "auto", "coreprotect", "prism");
        add(options, "rollback-on-round-end", ValueType.BOOLEAN, Category.ROLLBACK);
        add(options, "coreprotect-rollback-command", ValueType.STRING, Category.ROLLBACK);
        add(options, "prism-rollback-command", ValueType.STRING, Category.ROLLBACK);

        return Collections.unmodifiableMap(options);
    }

    private static void add(Map<String, Option> options, String path, ValueType type, Category category) {
        add(options, path, type, category, null, null, null, List.of());
    }

    private static void addNumber(
            Map<String, Option> options,
            String path,
            ValueType type,
            Category category,
            Double min,
            Double max,
            Double step
    ) {
        add(options, path, type, category, min, max, step, List.of());
    }

    private static void addEnum(Map<String, Option> options, String path, Category category, String... allowedValues) {
        add(options, path, ValueType.STRING, category, null, null, null, List.of(allowedValues));
    }

    private static void add(
            Map<String, Option> options,
            String path,
            ValueType type,
            Category category,
            Double min,
            Double max,
            Double step,
            List<String> allowedValues
    ) {
        String messageId = path.replace('.', '-');
        options.put(path, new Option(
                path,
                type,
                category,
                "config-gui.option." + messageId + ".label",
                "config-gui.option." + messageId + ".description",
                min,
                max,
                step,
                List.copyOf(allowedValues)
        ));
    }

    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw ValidationException.of("boolean");
        };
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ValidationException.of("integer");
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw ValidationException.of("integer");
        }
    }

    private static double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw ValidationException.of("decimal");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw ValidationException.of("decimal");
        }
    }

    private static Number validateNumber(Option option, Number value) {
        double numericValue = value.doubleValue();
        if (!Double.isFinite(numericValue)) {
            throw ValidationException.of("decimal");
        }
        if (option.min() != null && numericValue < option.min()) {
            throw ValidationException.of("min", "min", formatNumber(option.min()));
        }
        if (option.max() != null && numericValue > option.max()) {
            throw ValidationException.of("max", "max", formatNumber(option.max()));
        }
        return value;
    }

    private static String parseString(Option option, String value) {
        if (option.allowedValues().isEmpty()) {
            return value;
        }

        for (String allowed : option.allowedValues()) {
            if (allowed.equalsIgnoreCase(value)) {
                return allowed;
            }
        }
        throw ValidationException.of("allowed", "allowed", String.join(", ", option.allowedValues()));
    }

    private static List<String> parseStringList(String value) {
        List<String> entries = new ArrayList<>();
        for (String entry : value.split("[,\\r\\n]+")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        if (entries.isEmpty()) {
            throw ValidationException.of("list");
        }
        return List.copyOf(entries);
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public enum Category {
        BORDER("border"),
        ROUND("round"),
        START_LOBBY("start-lobby"),
        STARTER("starter"),
        SPECTATORS_DIMENSIONS("spectators-dimensions"),
        LUCKPERMS("luckperms"),
        ROLLBACK("rollback");

        private final String messageId;

        Category(String messageId) {
            this.messageId = messageId;
        }

        public String labelKey() {
            return "config-gui.category." + messageId + ".label";
        }

        public String descriptionKey() {
            return "config-gui.category." + messageId + ".description";
        }
    }

    public enum ValueType {
        BOOLEAN,
        INT,
        LONG,
        DOUBLE,
        STRING,
        STRING_LIST;

        public boolean numeric() {
            return this == INT || this == LONG || this == DOUBLE;
        }
    }

    public record Option(
            String path,
            ValueType type,
            Category category,
            String labelKey,
            String descriptionKey,
            Double min,
            Double max,
            Double step,
            List<String> allowedValues
    ) {
    }

    public static final class ValidationException extends IllegalArgumentException {

        private final String reason;
        private final Map<String, String> placeholders;

        private ValidationException(String reason, Map<String, String> placeholders) {
            this.reason = reason;
            this.placeholders = placeholders;
        }

        public static ValidationException of(String reason) {
            return new ValidationException(reason, Map.of());
        }

        public static ValidationException of(String reason, String name, String value) {
            return new ValidationException(reason, Map.of(name, value));
        }

        public String reason() {
            return reason;
        }

        public Map<String, String> placeholders() {
            return placeholders;
        }
    }
}
