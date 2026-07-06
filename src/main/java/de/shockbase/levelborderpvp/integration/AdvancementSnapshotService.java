package de.shockbase.levelborderpvp.integration;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class AdvancementSnapshotService {

    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final File snapshotFile;
    private final Set<UUID> restoringPlayers = new HashSet<>();

    private YamlConfiguration snapshotData;

    public AdvancementSnapshotService(Plugin plugin, LevelBorderSettings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.snapshotFile = new File(plugin.getDataFolder(), "advancements.yml");
        load();
    }

    public void beginRound(Collection<Player> players) {
        if (!settings.advancementBonusEnabled()) {
            return;
        }

        List<Advancement> managedAdvancements = managedAdvancements();
        List<String> managedAdvancementKeys = advancementKeys(managedAdvancements);
        snapshotData.set("managed-advancements", managedAdvancementKeys);

        for (Player player : players) {
            snapshotAndReset(player, managedAdvancements, managedAdvancementKeys);
        }

        save();
    }

    public void restoreOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restoreIfPending(player);
        }
    }

    public void restoreIfPending(Player player) {
        String playerPath = playerPath(player.getUniqueId());
        if (!snapshotData.contains(playerPath)) {
            return;
        }

        restoringPlayers.add(player.getUniqueId());
        try {
            revokeManagedAdvancements(player, managedAdvancementKeysForRestore(playerPath));
            restoreSnapshot(player, playerPath);
        } finally {
            restoringPlayers.remove(player.getUniqueId());
        }

        snapshotData.set(playerPath, null);
        if (!hasPlayerSnapshots()) {
            snapshotData.set("managed-advancements", null);
        }
        save();
    }

    public boolean isRestoring(Player player) {
        return restoringPlayers.contains(player.getUniqueId());
    }

    public boolean isManagedAdvancement(Advancement advancement) {
        if (advancement == null) {
            return false;
        }

        List<String> activeManagedKeys = snapshotData.getStringList("managed-advancements");
        String advancementKey = advancement.getKey().toString();
        if (!activeManagedKeys.isEmpty()) {
            return activeManagedKeys.contains(advancementKey);
        }

        return isConfiguredAdvancementKey(advancementKey);
    }

    private void load() {
        if (!snapshotFile.exists() && snapshotFile.getParentFile() != null) {
            snapshotFile.getParentFile().mkdirs();
        }
        snapshotData = YamlConfiguration.loadConfiguration(snapshotFile);
    }

    private void snapshotAndReset(Player player, List<Advancement> managedAdvancements, List<String> managedAdvancementKeys) {
        String playerPath = playerPath(player.getUniqueId());
        snapshotData.set(playerPath, null);
        snapshotData.set(playerPath + ".name", player.getName());
        snapshotData.set(playerPath + ".managed-advancements", managedAdvancementKeys);
        snapshotData.set(playerPath + ".advancements", null);

        for (Advancement advancement : managedAdvancements) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            List<String> awardedCriteria = new ArrayList<>(progress.getAwardedCriteria());
            if (!awardedCriteria.isEmpty()) {
                String advancementPath = playerPath + ".advancements." + encodedPathKey(advancement.getKey().toString());
                snapshotData.set(advancementPath + ".key", advancement.getKey().toString());
                snapshotData.set(advancementPath + ".criteria", awardedCriteria);
            }
            revokeAwardedCriteria(progress);
        }
    }

    private void restoreSnapshot(Player player, String playerPath) {
        ConfigurationSection advancements = snapshotData.getConfigurationSection(playerPath + ".advancements");
        if (advancements == null) {
            return;
        }

        for (String entryKey : advancements.getKeys(false)) {
            String entryPath = playerPath + ".advancements." + entryKey;
            String advancementKey = snapshotData.getString(entryPath + ".key");
            List<String> criteria = snapshotData.getStringList(entryPath + ".criteria");
            if (advancementKey == null || advancementKey.isBlank()) {
                advancementKey = entryKey;
                criteria = snapshotData.getStringList(entryPath);
            }

            Advancement advancement = advancementByKey(advancementKey);
            if (advancement == null) {
                continue;
            }

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : criteria) {
                if (progress.getRemainingCriteria().contains(criterion)) {
                    progress.awardCriteria(criterion);
                }
            }
        }
    }

    private void revokeManagedAdvancements(Player player, List<String> managedAdvancementKeys) {
        for (String advancementKey : managedAdvancementKeys) {
            Advancement advancement = advancementByKey(advancementKey);
            if (advancement != null) {
                revokeAwardedCriteria(player.getAdvancementProgress(advancement));
            }
        }
    }

    private void revokeAwardedCriteria(AdvancementProgress progress) {
        for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(criterion);
        }
    }

    private List<Advancement> managedAdvancements() {
        List<Advancement> advancements = new ArrayList<>();
        Iterator<Advancement> iterator = plugin.getServer().advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (isConfiguredAdvancementKey(advancement.getKey().toString())) {
                advancements.add(advancement);
            }
        }
        return advancements;
    }

    private List<String> advancementKeys(List<Advancement> advancements) {
        List<String> keys = new ArrayList<>();
        for (Advancement advancement : advancements) {
            keys.add(advancement.getKey().toString());
        }
        return keys;
    }

    private List<String> managedAdvancementKeysForRestore(String playerPath) {
        List<String> keys = snapshotData.getStringList(playerPath + ".managed-advancements");
        if (!keys.isEmpty()) {
            return keys;
        }

        keys = snapshotData.getStringList("managed-advancements");
        if (!keys.isEmpty()) {
            return keys;
        }

        return advancementKeys(managedAdvancements());
    }

    private boolean isConfiguredAdvancementKey(String advancementKey) {
        String normalizedKey = advancementKey.toLowerCase(Locale.ROOT);
        for (String excludedPrefix : settings.advancementExcludedPrefixes()) {
            if (excludedPrefix != null && normalizedKey.startsWith(excludedPrefix.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private Advancement advancementByKey(String advancementKey) {
        NamespacedKey key = NamespacedKey.fromString(advancementKey);
        return key == null ? null : plugin.getServer().getAdvancement(key);
    }

    private String playerPath(UUID playerId) {
        return "players." + playerId;
    }

    private String encodedPathKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasPlayerSnapshots() {
        ConfigurationSection players = snapshotData.getConfigurationSection("players");
        return players != null && !players.getKeys(false).isEmpty();
    }

    private void save() {
        try {
            snapshotData.save(snapshotFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, messages.text("log.advancement-snapshots-save-failed"), exception);
        }
    }
}
