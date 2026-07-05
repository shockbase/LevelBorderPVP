package de.shockbase.levelborderpvp.integration;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerRollbackService {

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;

    private RoundRollbackSnapshot snapshot = RoundRollbackSnapshot.empty();

    public PlayerRollbackService(Plugin plugin, LevelBorderSettings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    public void beginRound(Collection<? extends Player> players) {
        long startedAtMillis = System.currentTimeMillis();
        List<RollbackPlayer> rollbackPlayers = new ArrayList<>();

        for (Player player : players) {
            rollbackPlayers.add(new RollbackPlayer(
                    player.getUniqueId(),
                    player.getName(),
                    player.getWorld().getName()
            ));
        }

        snapshot = new RoundRollbackSnapshot(startedAtMillis, 0L, List.copyOf(rollbackPlayers));
    }

    public void markRoundEnded() {
        if (snapshot.isEmpty() || snapshot.endedAtMillis() > 0L) {
            return;
        }

        snapshot = snapshot.withEndedAtMillis(System.currentTimeMillis());
    }

    public RollbackResult rollbackConfiguredProvider() {
        return rollback(settings.rollbackProvider());
    }

    public RollbackResult rollback(String requestedProvider) {
        if (!settings.rollbackIntegrationEnabled()) {
            return RollbackResult.disabled();
        }
        if (snapshot.isEmpty()) {
            return RollbackResult.noPlayers();
        }

        RollbackProvider provider = resolveProvider(requestedProvider);
        if (provider == RollbackProvider.UNKNOWN) {
            return RollbackResult.unknownProvider(providerName(requestedProvider));
        }
        if (provider == RollbackProvider.NONE) {
            return RollbackResult.providerMissing(providerName(requestedProvider));
        }

        String commandTemplate = commandTemplate(provider);
        if (commandTemplate == null || commandTemplate.isBlank()) {
            return RollbackResult.commandMissing(provider.configValue());
        }

        int commandCount = 0;
        for (RollbackPlayer player : snapshot.players()) {
            String command = formatCommand(commandTemplate, provider, player);
            if (runCommand(command)) {
                commandCount++;
            }
        }

        return RollbackResult.started(provider.configValue(), snapshot.players().size(), commandCount);
    }

    private RollbackProvider resolveProvider(String requestedProvider) {
        String provider = requestedProvider;
        if (provider == null || provider.isBlank()) {
            provider = settings.rollbackProvider();
        }

        String normalized = provider == null ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto" -> resolveAutomaticProvider();
            case "coreprotect" -> isPluginEnabled("CoreProtect") ? RollbackProvider.COREPROTECT : RollbackProvider.NONE;
            case "prism" -> isPluginEnabled("Prism") ? RollbackProvider.PRISM : RollbackProvider.NONE;
            default -> RollbackProvider.UNKNOWN;
        };
    }

    private RollbackProvider resolveAutomaticProvider() {
        if (isPluginEnabled("CoreProtect")) {
            return RollbackProvider.COREPROTECT;
        }
        if (isPluginEnabled("Prism")) {
            return RollbackProvider.PRISM;
        }
        return RollbackProvider.NONE;
    }

    private boolean isPluginEnabled(String pluginName) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return found != null && found.isEnabled();
    }

    private String commandTemplate(RollbackProvider provider) {
        return switch (provider) {
            case COREPROTECT -> settings.coreProtectRollbackCommand();
            case PRISM -> settings.prismRollbackCommand();
            case NONE, UNKNOWN -> "";
        };
    }

    private String formatCommand(String commandTemplate, RollbackProvider provider, RollbackPlayer player) {
        long nowMillis = System.currentTimeMillis();
        long roundEndMillis = snapshot.endedAtMillis() > 0L ? snapshot.endedAtMillis() : nowMillis;
        long durationSeconds = secondsBetween(snapshot.startedAtMillis(), nowMillis);
        long roundDurationSeconds = secondsBetween(snapshot.startedAtMillis(), roundEndMillis);

        return commandTemplate
                .replace("{provider}", provider.configValue())
                .replace("{player}", player.name())
                .replace("{uuid}", player.uuid().toString())
                .replace("{world}", player.worldName())
                .replace("{duration}", durationSeconds + "s")
                .replace("{duration_seconds}", Long.toString(durationSeconds))
                .replace("{round_duration}", roundDurationSeconds + "s")
                .replace("{round_duration_seconds}", Long.toString(roundDurationSeconds));
    }

    private long secondsBetween(long startedAtMillis, long endedAtMillis) {
        return Math.max(1L, (long) Math.ceil((endedAtMillis - startedAtMillis) / 1000.0D));
    }

    private boolean runCommand(String command) {
        ConsoleCommandSender console = plugin.getServer().getConsoleSender();
        try {
            boolean dispatched = plugin.getServer().dispatchCommand(console, command);
            if (!dispatched) {
                plugin.getLogger().warning(messages.text(
                        "log.rollback-command-failed",
                        Messages.placeholder("command", command)
                ));
            }
            return dispatched;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, messages.text(
                    "log.rollback-command-failed",
                    Messages.placeholder("command", command)
            ), exception);
            return false;
        }
    }

    private String providerName(String requestedProvider) {
        if (requestedProvider == null || requestedProvider.isBlank()) {
            return settings.rollbackProvider();
        }
        return requestedProvider;
    }

    private enum RollbackProvider {
        COREPROTECT("coreprotect"),
        PRISM("prism"),
        NONE("none"),
        UNKNOWN("unknown");

        private final String configValue;

        RollbackProvider(String configValue) {
            this.configValue = configValue;
        }

        String configValue() {
            return configValue;
        }
    }

    public enum RollbackStatus {
        STARTED,
        DISABLED,
        NO_PLAYERS,
        PROVIDER_MISSING,
        UNKNOWN_PROVIDER,
        COMMAND_MISSING
    }

    public record RollbackResult(
            RollbackStatus status,
            String provider,
            int players,
            int commands
    ) {
        public static RollbackResult started(String provider, int players, int commands) {
            return new RollbackResult(RollbackStatus.STARTED, provider, players, commands);
        }

        public static RollbackResult disabled() {
            return new RollbackResult(RollbackStatus.DISABLED, "", 0, 0);
        }

        public static RollbackResult noPlayers() {
            return new RollbackResult(RollbackStatus.NO_PLAYERS, "", 0, 0);
        }

        public static RollbackResult providerMissing(String provider) {
            return new RollbackResult(RollbackStatus.PROVIDER_MISSING, provider, 0, 0);
        }

        public static RollbackResult unknownProvider(String provider) {
            return new RollbackResult(RollbackStatus.UNKNOWN_PROVIDER, provider, 0, 0);
        }

        public static RollbackResult commandMissing(String provider) {
            return new RollbackResult(RollbackStatus.COMMAND_MISSING, provider, 0, 0);
        }
    }

    private record RollbackPlayer(UUID uuid, String name, String worldName) {
    }

    private record RoundRollbackSnapshot(
            long startedAtMillis,
            long endedAtMillis,
            List<RollbackPlayer> players
    ) {
        static RoundRollbackSnapshot empty() {
            return new RoundRollbackSnapshot(0L, 0L, List.of());
        }

        boolean isEmpty() {
            return startedAtMillis <= 0L || players.isEmpty();
        }

        RoundRollbackSnapshot withEndedAtMillis(long endedAtMillis) {
            return new RoundRollbackSnapshot(startedAtMillis, endedAtMillis, players);
        }
    }
}
