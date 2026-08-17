package ru.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.core.Core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Configs {

    private final Core plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration lang;
    private FileConfiguration placeholders;
    private FileConfiguration animations;
    private FileConfiguration groups;

    public Configs(Core plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = read("config.yml");
        messages = read("messages.yml");
        lang = read("lang.yml");
        placeholders = read("placeholders.yml");
        animations = read("animations.yml");
        groups = read("groups.yml");
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public FileConfiguration lang() {
        return lang;
    }

    public FileConfiguration placeholders() {
        return placeholders;
    }

    public FileConfiguration animations() {
        return animations;
    }

    public FileConfiguration groups() {
        return groups;
    }

    public List<String> messages(String path) {
        List<String> lines = messages.getStringList(path);
        if (lines.isEmpty()) {
            String single = messages.getString(path);
            if (single == null) {
                List<String> missing = new ArrayList<>(1);
                missing.add("&cОтсутствует сообщение: &f" + path);
                return missing;
            }
            lines = new ArrayList<>(1);
            lines.add(single);
        }
        String prefix = prefix();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(line.replace("%PREFIX%", prefix));
        }
        return result;
    }

    public String prefix() {
        List<String> lines = messages.getStringList("PREFIX");
        return lines.isEmpty() ? "" : lines.get(0);
    }

    public String defaultLanguage() {
        String value = lang.getString("DEFAULT", "RU");
        return value.toUpperCase(Locale.ROOT);
    }

    public List<String> languages() {
        List<String> result = new ArrayList<>();
        for (String code : lang.getStringList("ENABLED")) {
            String upper = code.toUpperCase(Locale.ROOT);
            if (!result.contains(upper)) {
                result.add(upper);
            }
        }
        if (result.isEmpty()) {
            result.add(defaultLanguage());
        }
        return result;
    }

    private FileConfiguration read(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            return configuration;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            Set<String> keys = new HashSet<>(configuration.getKeys(true));
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
            if (hasMissingKeys(defaults, keys)) {
                configuration.save(file);
                plugin.getLogger().info("Обновлена конфигурация " + name + ": добавлены отсутствующие ключи.");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось обновить ресурс " + name + ": " + exception.getMessage());
        }
        return configuration;
    }

    private boolean hasMissingKeys(YamlConfiguration defaults, Set<String> keys) {
        for (String key : defaults.getKeys(true)) {
            if (!keys.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
