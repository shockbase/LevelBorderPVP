package de.shockbase.levelborderpvp.command;

import de.shockbase.levelborderpvp.border.BorderService;
import de.shockbase.levelborderpvp.config.ConfigSchema;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.gui.ConfigDialog;
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
import java.util.List;
import java.util.Locale;

public final class BorderCommand implements CommandExecutor, TabCompleter {

    private final LevelBorderSettings settings;
    private final BorderService borderService;
    private final Messages messages;
    private final Plugin plugin;
    private final ConfigDialog configDialog;

    public BorderCommand(Plugin plugin, LevelBorderSettings settings, BorderService borderService, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.borderService = borderService;
        this.messages = messages;
        this.configDialog = new ConfigDialog(settings, borderService, messages, this::canControlGame);
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
            return matching(ConfigSchema.options().stream().map(ConfigSchema.Option::path).toList(), args[2]);
        }
        if (args.length == 4 && "set".equalsIgnoreCase(args[1])) {
            ConfigSchema.Option option = ConfigSchema.find(args[2]);
            if (option == null) {
                return Collections.emptyList();
            }
            return matching(option.allowedValues(), args[3]);
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
                Messages.placeholder("seconds", startResult.countdownSeconds())
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
        if (args.length == 0 && sender instanceof Player player) {
            configDialog.open(player);
            return true;
        }

        if (args.length == 0 || (args.length == 1 && "list".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(messages.text("command.config-list-header"));
            for (ConfigSchema.Option option : ConfigSchema.options()) {
                sender.sendMessage(messages.text(
                        "command.config-entry",
                        Messages.placeholder("key", option.path()),
                        Messages.placeholder("value", ConfigSchema.format(settings.configValue(option.path())))
                ));
            }
            return true;
        }

        if (args.length == 2 && "get".equalsIgnoreCase(args[0])) {
            ConfigSchema.Option option = ConfigSchema.find(args[1]);
            if (option == null) {
                sender.sendMessage(messages.text("command.config-unknown", Messages.placeholder("key", args[1])));
                return true;
            }

            sender.sendMessage(messages.text(
                    "command.config-entry",
                    Messages.placeholder("key", option.path()),
                    Messages.placeholder("value", ConfigSchema.format(settings.configValue(option.path())))
            ));
            return true;
        }

        if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            ConfigSchema.Option option = ConfigSchema.find(args[1]);
            if (option == null) {
                sender.sendMessage(messages.text("command.config-unknown", Messages.placeholder("key", args[1])));
                return true;
            }

            String rawValue = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            Object parsedValue;
            try {
                parsedValue = ConfigSchema.parse(option, rawValue);
            } catch (ConfigSchema.ValidationException exception) {
                sender.sendMessage(messages.text(
                        "command.config-invalid",
                        Messages.placeholder("reason", validationMessage(exception))
                ));
                return true;
            }

            settings.setConfigValue(option.path(), parsedValue);
            borderService.refreshRuntimeSettings();
            sender.sendMessage(messages.text(
                    "command.config-set",
                    Messages.placeholder("key", option.path()),
                    Messages.placeholder("value", ConfigSchema.format(parsedValue))
            ));
            return true;
        }

        sender.sendMessage(messages.text("command.config-usage", Messages.placeholder("label", label)));
        return true;
    }

    private String validationMessage(ConfigSchema.ValidationException exception) {
        return messages.text(
                "config-gui.validation." + exception.reason(),
                Messages.placeholder("min", exception.placeholders().getOrDefault("min", "")),
                Messages.placeholder("max", exception.placeholders().getOrDefault("max", "")),
                Messages.placeholder("allowed", exception.placeholders().getOrDefault("allowed", ""))
        );
    }
}
