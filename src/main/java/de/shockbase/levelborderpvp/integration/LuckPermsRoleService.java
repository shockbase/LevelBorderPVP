package de.shockbase.levelborderpvp.integration;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class LuckPermsRoleService {

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;

    public LuckPermsRoleService(Plugin plugin, LevelBorderSettings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    public void markActive(Player player) {
        if (!settings.luckPermsIntegrationEnabled()) {
            return;
        }

        runConfiguredCommand(settings.luckPermsRemoveSpectatorCommand(), player);
        runConfiguredCommand(settings.luckPermsAddActiveCommand(), player);
    }

    public void markSpectator(Player player) {
        if (!settings.luckPermsIntegrationEnabled()) {
            return;
        }

        runConfiguredCommand(settings.luckPermsRemoveActiveCommand(), player);
        runConfiguredCommand(settings.luckPermsAddSpectatorCommand(), player);
    }

    public void clear(Player player) {
        if (!settings.luckPermsIntegrationEnabled() || !settings.luckPermsClearGroupsOnRoundEnd()) {
            return;
        }

        runConfiguredCommand(settings.luckPermsRemoveActiveCommand(), player);
        runConfiguredCommand(settings.luckPermsRemoveSpectatorCommand(), player);
    }

    private void runConfiguredCommand(String commandTemplate, Player player) {
        if (commandTemplate == null || commandTemplate.isBlank()) {
            return;
        }

        String command = commandTemplate
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{active_group}", settings.luckPermsActiveGroup())
                .replace("{spectator_group}", settings.luckPermsSpectatorGroup());

        ConsoleCommandSender console = plugin.getServer().getConsoleSender();
        try {
            plugin.getServer().dispatchCommand(console, command);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, messages.text("log.luckperms-command-failed", Messages.placeholder("command", command)), exception);
        }
    }
}
