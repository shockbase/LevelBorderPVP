package de.shockbase.levelborderpvp.command;

import de.shockbase.levelborderpvp.border.BorderService;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.PlayerRollbackService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class BorderCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, ConfigOption> CONFIG_OPTIONS = createConfigOptions();

    private final LevelBorderSettings settings;
    private final BorderService borderService;
    private final Messages messages;
    private final Plugin plugin;

    public BorderCommand(Plugin plugin, LevelBorderSettings settings, BorderService borderService, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.borderService = borderService;
        this.messages = messages;
    }

    private static Map<String, ConfigOption> createConfigOptions() {
        Map<String, ConfigOption> options = new LinkedHashMap<>();
        add(options, "initial-size-blocks", ConfigValueType.DOUBLE);
        add(options, "growth-per-level-blocks", ConfigValueType.DOUBLE);
        add(options, "level-mode", ConfigValueType.STRING, "highest", "current");
        add(options, "highest-kill-bonus-enabled", ConfigValueType.BOOLEAN);
        add(options, "highest-kill-bonus-inherits-victim-bonus", ConfigValueType.BOOLEAN);
        add(options, "lobby-radius-blocks", ConfigValueType.DOUBLE);
        add(options, "teleport-players-to-lobby-spawn", ConfigValueType.BOOLEAN);
        add(options, "center-at-block-center", ConfigValueType.BOOLEAN);
        add(options, "dimension-policy", ConfigValueType.STRING, "safe-pve", "legacy");
        add(options, "max-size-blocks", ConfigValueType.DOUBLE);
        add(options, "border-transition-seconds", ConfigValueType.LONG);
        add(options, "start-countdown-seconds", ConfigValueType.INT);
        add(options, "minimum-start-players", ConfigValueType.INT);
        add(options, "max-start-countdown-seconds", ConfigValueType.INT);
        add(options, "reset-xp-on-start", ConfigValueType.BOOLEAN);
        add(options, "clear-inventory-on-start", ConfigValueType.BOOLEAN);
        add(options, "reapply-on-world-change", ConfigValueType.BOOLEAN);
        add(options, "reapply-on-respawn", ConfigValueType.BOOLEAN);
        add(options, "command-permission", ConfigValueType.STRING);
        add(options, "end-condition", ConfigValueType.STRING, "timed-score", "target-level", "target-border", "elimination", "disabled");
        add(options, "round-duration-minutes", ConfigValueType.INT);
        add(options, "score-tiebreakers", ConfigValueType.STRING_LIST, "kills,highest-level,deaths-ascending");
        add(options, "win-target-level", ConfigValueType.INT);
        add(options, "win-target-border-size-blocks", ConfigValueType.DOUBLE);
        add(options, "breakout-grace-seconds", ConfigValueType.INT);
        add(options, "luckperms-integration-enabled", ConfigValueType.BOOLEAN);
        add(options, "luckperms-active-group", ConfigValueType.STRING);
        add(options, "luckperms-spectator-group", ConfigValueType.STRING);
        add(options, "luckperms-clear-groups-on-round-end", ConfigValueType.BOOLEAN);
        add(options, "luckperms-command-add-active", ConfigValueType.STRING);
        add(options, "luckperms-command-remove-active", ConfigValueType.STRING);
        add(options, "luckperms-command-add-spectator", ConfigValueType.STRING);
        add(options, "luckperms-command-remove-spectator", ConfigValueType.STRING);
        add(options, "rollback-integration-enabled", ConfigValueType.BOOLEAN);
        add(options, "rollback-provider", ConfigValueType.STRING, "auto", "coreprotect", "prism");
        add(options, "rollback-on-round-end", ConfigValueType.BOOLEAN);
        add(options, "coreprotect-rollback-command", ConfigValueType.STRING);
        add(options, "prism-rollback-command", ConfigValueType.STRING);
        return Collections.unmodifiableMap(options);
    }

    private static void add(Map<String, ConfigOption> options, String path, ConfigValueType type, String... suggestions) {
        options.put(path, new ConfigOption(path, type, List.of(suggestions)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canControlGame(sender)) {
            sender.sendMessage(messages.text(
                    "command.no-permission",
                    Messages.placeholder("permission", settings.commandPermission())
            ));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messages.text("command.usage", Messages.placeholder("label", label)));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        return handleCommand(sender, subCommand, label, subCommandArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canControlGame(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return matching(List.of("lobby", "start", "stop", "rollback", "config"), args[0]);
        }
        if ("config".equalsIgnoreCase(args[0])) {
            return tabCompleteConfig(args);
        }
        if (args.length == 2 && "rollback".equalsIgnoreCase(args[0])) {
            return matching(List.of("auto", "coreprotect", "prism"), args[1]);
        }
        if (args.length != 2 || !"start".equalsIgnoreCase(args[0])) {
            return Collections.emptyList();
        }

        return matching(List.of(
                Integer.toString(settings.defaultStartCountdownSeconds()),
                "0",
                "5",
                "10"
        ), args[1]);
    }

    private List<String> tabCompleteConfig(String[] args) {
        if (args.length == 2) {
            return matching(List.of("list", "get", "set"), args[1]);
        }
        if (args.length == 3 && ("get".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
            return matching(new ArrayList<>(CONFIG_OPTIONS.keySet()), args[2]);
        }
        if (args.length == 4 && "set".equalsIgnoreCase(args[1])) {
            ConfigOption option = CONFIG_OPTIONS.get(args[2].toLowerCase(Locale.ROOT));
            if (option == null) {
                return Collections.emptyList();
            }
            return matching(option.valueSuggestions(), args[3]);
        }
        return Collections.emptyList();
    }

    private boolean canControlGame(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }

        if (isLuckPermsEnabled()) {
            return sender.hasPermission(settings.commandPermission());
        }

        return sender instanceof Player && sender.isOp();
    }

    private boolean isLuckPermsEnabled() {
        Plugin luckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        return luckPerms != null && luckPerms.isEnabled();
    }

    private List<String> matching(List<String> suggestions, String currentArgument) {
        String current = currentArgument.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion.startsWith(current)) {
                matches.add(suggestion);
            }
        }
        return matches;
    }

    private boolean handleCommand(CommandSender sender, String subCommand, String label, String[] args) {
        if ("lobby".equals(subCommand)) {
            return handleLobby(sender, label, args);
        }
        if ("start".equals(subCommand)) {
            return handleStart(sender, label, args);
        }
        if ("stop".equals(subCommand)) {
            return handleStop(sender, label, args);
        }
        if ("rollback".equals(subCommand)) {
            return handleRollback(sender, label, args);
        }
        if ("config".equals(subCommand)) {
            return handleConfig(sender, label, args);
        }

        sender.sendMessage(messages.text("command.usage", Messages.placeholder("label", label)));
        return true;
    }

    private boolean handleLobby(CommandSender sender, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(messages.text("command.lobby-usage", Messages.placeholder("label", label)));
            return true;
        }

        borderService.lobby();
        sender.sendMessage(messages.text("command.lobby"));
        return true;
    }

    private boolean handleStart(CommandSender sender, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(messages.text("command.start-usage", Messages.placeholder("label", label)));
            return true;
        }

        int countdownSeconds = settings.defaultStartCountdownSeconds();
        if (args.length == 1) {
            try {
                countdownSeconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(messages.text("command.invalid-seconds"));
                return true;
            }
        }

        if (countdownSeconds < 0) {
            sender.sendMessage(messages.text("command.negative-countdown"));
            return true;
        }
        if (countdownSeconds > settings.maxStartCountdownSeconds()) {
            sender.sendMessage(messages.text(
                    "command.max-countdown",
                    Messages.placeholder("seconds", settings.maxStartCountdownSeconds())
            ));
            return true;
        }

        BorderService.StartResult startResult = borderService.start(countdownSeconds);
        if (!startResult.started()) {
            sender.sendMessage(messages.text(
                    "command.not-enough-players",
                    Messages.placeholder("players", startResult.eligiblePlayers()),
                    Messages.placeholder("required", startResult.requiredPlayers())
            ));
            return true;
        }

        sender.sendMessage(messages.text(
                "command.starting",
                Messages.placeholder("seconds", countdownSeconds)
        ));
        return true;
    }

    private boolean handleStop(CommandSender sender, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(messages.text("command.stop-usage", Messages.placeholder("label", label)));
            return true;
        }

        borderService.stop();
        sender.sendMessage(messages.text("command.stopped"));
        return true;
    }

    private boolean handleRollback(CommandSender sender, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(messages.text("command.rollback-usage", Messages.placeholder("label", label)));
            return true;
        }

        String provider = args.length == 1 ? args[0] : null;
        PlayerRollbackService.RollbackResult result = borderService.rollbackRoundChanges(provider);
        sender.sendMessage(rollbackMessage(result));
        return true;
    }

    private String rollbackMessage(PlayerRollbackService.RollbackResult result) {
        return switch (result.status()) {
            case STARTED -> messages.text(
                    "command.rollback-started",
                    Messages.placeholder("provider", result.provider()),
                    Messages.placeholder("players", result.players()),
                    Messages.placeholder("commands", result.commands())
            );
            case DISABLED -> messages.text("command.rollback-disabled");
            case NO_PLAYERS -> messages.text("command.rollback-no-players");
            case PROVIDER_MISSING -> messages.text(
                    "command.rollback-provider-missing",
                    Messages.placeholder("provider", result.provider())
            );
            case UNKNOWN_PROVIDER -> messages.text(
                    "command.rollback-provider-unknown",
                    Messages.placeholder("provider", result.provider())
            );
            case COMMAND_MISSING -> messages.text(
                    "command.rollback-command-missing",
                    Messages.placeholder("provider", result.provider())
            );
        };
    }

    private boolean handleConfig(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && "list".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(messages.text("command.config-list-header"));
            for (ConfigOption option : CONFIG_OPTIONS.values()) {
                sender.sendMessage(messages.text(
                        "command.config-entry",
                        Messages.placeholder("key", option.path()),
                        Messages.placeholder("value", formatConfigValue(settings.configValue(option.path())))
                ));
            }
            return true;
        }

        if (args.length == 2 && "get".equalsIgnoreCase(args[0])) {
            ConfigOption option = findConfigOption(args[1]);
            if (option == null) {
                sender.sendMessage(messages.text("command.config-unknown", Messages.placeholder("key", args[1])));
                return true;
            }

            sender.sendMessage(messages.text(
                    "command.config-entry",
                    Messages.placeholder("key", option.path()),
                    Messages.placeholder("value", formatConfigValue(settings.configValue(option.path())))
            ));
            return true;
        }

        if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            ConfigOption option = findConfigOption(args[1]);
            if (option == null) {
                sender.sendMessage(messages.text("command.config-unknown", Messages.placeholder("key", args[1])));
                return true;
            }

            String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            Object parsedValue;
            try {
                parsedValue = parseConfigValue(option, rawValue);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(messages.text("command.config-invalid", Messages.placeholder("reason", exception.getMessage())));
                return true;
            }

            settings.setConfigValue(option.path(), parsedValue);
            borderService.refreshRuntimeSettings();
            sender.sendMessage(messages.text(
                    "command.config-set",
                    Messages.placeholder("key", option.path()),
                    Messages.placeholder("value", formatConfigValue(parsedValue))
            ));
            return true;
        }

        sender.sendMessage(messages.text("command.config-usage", Messages.placeholder("label", label)));
        return true;
    }

    private ConfigOption findConfigOption(String key) {
        if (key == null) {
            return null;
        }
        return CONFIG_OPTIONS.get(key.toLowerCase(Locale.ROOT));
    }

    private Object parseConfigValue(ConfigOption option, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value is empty");
        }

        return switch (option.type()) {
            case BOOLEAN -> parseBoolean(value);
            case INT -> parseInteger(value);
            case LONG -> parseLong(value);
            case DOUBLE -> parseDouble(value);
            case STRING -> parseString(option, value);
            case STRING_LIST -> parseStringList(value);
        };
    }

    private boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("expected true/false");
        };
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected whole number");
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected whole number");
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected decimal number");
        }
    }

    private String parseString(ConfigOption option, String value) {
        if (option.valueSuggestions().isEmpty()) {
            return value;
        }

        for (String allowed : option.valueSuggestions()) {
            if (allowed.equalsIgnoreCase(value)) {
                return allowed;
            }
        }

        throw new IllegalArgumentException("allowed: " + String.join(", ", option.valueSuggestions()));
    }

    private List<String> parseStringList(String value) {
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("expected comma-separated values");
        }
        return entries;
    }

    private String formatConfigValue(Object value) {
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(",");
            for (Object entry : list) {
                joiner.add(String.valueOf(entry));
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    private enum ConfigValueType {
        BOOLEAN,
        INT,
        LONG,
        DOUBLE,
        STRING,
        STRING_LIST
    }

    private record ConfigOption(String path, ConfigValueType type, List<String> valueSuggestions) {
    }
}
