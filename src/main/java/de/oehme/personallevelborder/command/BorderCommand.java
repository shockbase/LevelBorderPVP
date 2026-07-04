package de.oehme.personallevelborder.command;

import de.oehme.personallevelborder.border.BorderService;
import de.oehme.personallevelborder.config.LevelBorderSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BorderCommand implements CommandExecutor, TabCompleter {

    private final LevelBorderSettings settings;
    private final BorderService borderService;

    public BorderCommand(LevelBorderSettings settings, BorderService borderService) {
        this.settings = settings;
        this.borderService = borderService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Nutzung: /" + label + " <start|stop|reset> [sekunden]");
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        return handlePlayerCommand(player, subCommand, label, subCommandArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(List.of("start", "stop", "reset"), args[0]);
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

    private boolean handlePlayerCommand(Player player, String subCommand, String label, String[] args) {
        if ("start".equals(subCommand)) {
            return handleStart(player, label, args);
        }
        if ("stop".equals(subCommand)) {
            return handleStop(player, label, args);
        }
        if ("reset".equals(subCommand)) {
            return handleReset(player, label, args);
        }

        player.sendMessage("Nutzung: /" + label + " <start|stop|reset> [sekunden]");
        return true;
    }

    private boolean handleStart(Player player, String label, String[] args) {
        if (args.length > 1) {
            player.sendMessage("Nutzung: /" + label + " start [sekunden]");
            return true;
        }

        int countdownSeconds = settings.defaultStartCountdownSeconds();
        if (args.length == 1) {
            try {
                countdownSeconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                player.sendMessage("Bitte Sekunden als ganze Zahl angeben.");
                return true;
            }
        }

        if (countdownSeconds < 0) {
            player.sendMessage("Der Countdown darf nicht negativ sein.");
            return true;
        }
        if (countdownSeconds > settings.maxStartCountdownSeconds()) {
            player.sendMessage("Der Countdown darf maximal " + settings.maxStartCountdownSeconds() + " Sekunden betragen.");
            return true;
        }

        borderService.start(player, countdownSeconds);
        return true;
    }

    private boolean handleStop(Player player, String label, String[] args) {
        if (args.length != 0) {
            player.sendMessage("Nutzung: /" + label + " stop");
            return true;
        }

        borderService.stop(player);
        player.sendMessage("Border gestoppt.");
        return true;
    }

    private boolean handleReset(Player player, String label, String[] args) {
        if (args.length != 0) {
            player.sendMessage("Nutzung: /" + label + " reset");
            return true;
        }

        borderService.reset(player);
        player.sendMessage("Border zurueckgesetzt.");
        return true;
    }
}
