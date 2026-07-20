package de.shockbase.levelborderpvp.i18n;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Messages {

    public static final String DEFAULT_LANGUAGE = "de";

    private final JavaPlugin plugin;
    private final String language;

    private FileConfiguration configuredMessages;
    private FileConfiguration bundledConfiguredMessages;
    private FileConfiguration defaultMessages;

    public Messages(JavaPlugin plugin, String language) {
        this.plugin = plugin;
        this.language = normalizeLanguage(language);
    }

    public void load() {
        saveBundledLanguage("de");
        saveBundledLanguage("en");
        saveBundledLanguage("ru");

        defaultMessages = loadBundledLanguage(DEFAULT_LANGUAGE);
        bundledConfiguredMessages = loadBundledLanguage(language);

        File configuredFile = languageFile(language);
        if (!configuredFile.isFile()) {
            configuredMessages = YamlConfiguration.loadConfiguration(languageFile(DEFAULT_LANGUAGE));
            plugin.getLogger().warning(format(
                    defaultMessages.getString("log.language-file-missing", "Language file lang/{language}.yml not found. Falling back to {fallback}."),
                    placeholder("language", language),
                    placeholder("fallback", DEFAULT_LANGUAGE)
            ));
            return;
        }

        configuredMessages = YamlConfiguration.loadConfiguration(configuredFile);
    }

    public String text(String key, Placeholder... placeholders) {
        String message = configuredMessages == null ? null : configuredMessages.getString(key);
        if (message == null) {
            message = bundledConfiguredMessages == null ? null : bundledConfiguredMessages.getString(key);
        }
        if (message == null) {
            message = defaultMessages == null ? null : defaultMessages.getString(key);
        }
        return format(message == null ? key : message, placeholders);
    }

    public String language() {
        return language;
    }

    public static Placeholder placeholder(String name, Object value) {
        return new Placeholder(name, String.valueOf(value));
    }

    private void saveBundledLanguage(String language) {
        String resourcePath = "lang/" + language + ".yml";
        File targetFile = new File(plugin.getDataFolder(), resourcePath);
        if (targetFile.isFile()) {
            return;
        }

        plugin.saveResource(resourcePath, false);
    }

    private FileConfiguration loadBundledLanguage(String language) {
        String resourcePath = "lang/" + language + ".yml";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return new YamlConfiguration();
            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load bundled language file " + resourcePath + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private File languageFile(String language) {
        return new File(plugin.getDataFolder(), "lang/" + language + ".yml");
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? DEFAULT_LANGUAGE : normalized;
    }

    private static String format(String message, Placeholder... placeholders) {
        String formatted = message;
        for (Placeholder placeholder : placeholders) {
            formatted = formatted.replace("{" + placeholder.name() + "}", placeholder.value());
        }
        return formatted;
    }

    public record Placeholder(String name, String value) {
    }
}
