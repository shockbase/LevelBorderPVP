package de.oehme.personallevelborder;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.oehme.personallevelborder.border.BorderNotification;
import de.oehme.personallevelborder.border.BorderNotifier;
import de.oehme.personallevelborder.border.BorderService;
import de.oehme.personallevelborder.border.BorderSizeCalculator;
import de.oehme.personallevelborder.border.BorderSizeFormatter;
import de.oehme.personallevelborder.command.BorderCommand;
import de.oehme.personallevelborder.config.LevelBorderSettings;
import de.oehme.personallevelborder.data.PlayerBorderRepository;
import de.oehme.personallevelborder.listener.PlayerBorderListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PersonalLevelBorderPlugin extends JavaPlugin {

    private PlayerBorderRepository playerBorderRepository;
    private BorderService borderService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        WorldBorderApi worldBorderApi = findWorldBorderApi();
        if (worldBorderApi == null) {
            getLogger().severe("WorldBorderAPI service not found. Is the WorldBorderAPI plugin installed and loaded?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LevelBorderSettings settings = new LevelBorderSettings(getConfig());
        BorderSizeCalculator sizeCalculator = new BorderSizeCalculator(settings);
        BorderSizeFormatter sizeFormatter = new BorderSizeFormatter();
        BorderNotifier notifier = new BorderNotifier(sizeFormatter);

        playerBorderRepository = new PlayerBorderRepository(getDataFolder(), getLogger());
        playerBorderRepository.load();

        borderService = new BorderService(
                this,
                worldBorderApi,
                playerBorderRepository,
                settings,
                sizeCalculator,
                notifier
        );

        getServer().getPluginManager().registerEvents(
                new PlayerBorderListener(settings, borderService),
                this
        );

        BorderCommand borderCommand = new BorderCommand(settings, borderService);
        registerCommand("levelborder", borderCommand);

        for (Player player : Bukkit.getOnlinePlayers()) {
            borderService.applyNextTick(player, BorderNotification.NONE);
        }
    }

    @Override
    public void onDisable() {
        if (borderService != null) {
            borderService.shutdown();
        }
        if (playerBorderRepository != null) {
            playerBorderRepository.save();
        }
    }

    private WorldBorderApi findWorldBorderApi() {
        RegisteredServiceProvider<WorldBorderApi> provider = getServer()
                .getServicesManager()
                .getRegistration(WorldBorderApi.class);

        return provider == null ? null : provider.getProvider();
    }

    private void registerCommand(String name, BorderCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml");
            return;
        }

        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
