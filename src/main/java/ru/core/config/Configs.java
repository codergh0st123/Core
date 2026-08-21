package ru.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.core.Core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Configs {

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");

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

        YamlConfiguration configuration = loadConfiguration(file, name);
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            return configuration;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            if (addMissingRoots(configuration, defaults)) {
                configuration.save(file);
                plugin.getLogger().info("Обновлена конфигурация " + name + ": добавлены отсутствующие корневые разделы.");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось обновить ресурс " + name + ": " + exception.getMessage());
        }
        return configuration;
    }

    private YamlConfiguration loadConfiguration(File file, String name) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            if (name.equals("lang.yml") && repairLanguageIndentation(file)) {
                return loadConfiguration(file, name);
            }
            plugin.getLogger().severe("Не удалось загрузить " + name + ": " + exception.getMessage());
            return configuration;
        }
    }

    private boolean repairLanguageIndentation(File file) {
        try {
            List<String> source = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> repaired = repairLanguageLists(source);
            if (repaired == null || !validYaml(repaired)) {
                return false;
            }

            File backup = new File(file.getParentFile(), file.getName() + ".invalid-"
                    + LocalDateTime.now().format(BACKUP_TIME) + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            Files.write(file.toPath(), repaired, StandardCharsets.UTF_8);
            plugin.getLogger().warning("Исправлены отступы списков в lang.yml. Исходный файл сохранён: " + backup.getName());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось автоматически исправить lang.yml: " + exception.getMessage());
            return false;
        }
    }

    static List<String> repairLanguageLists(List<String> source) {
        List<String> repaired = new ArrayList<>(source.size());
        int parentIndent = -1;
        boolean changed = false;

        for (String line : source) {
            int mappingIndent = mappingIndent(line);
            if (mappingIndent >= 0) {
                parentIndent = mappingIndent;
                repaired.add(line);
                continue;
            }

            if (parentIndent >= 0 && listEntry(line) && indentation(line) <= parentIndent) {
                repaired.add(" ".repeat(parentIndent + 2) + line.stripLeading());
                changed = true;
                continue;
            }

            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && indentation(line) <= parentIndent) {
                parentIndent = -1;
            }
            repaired.add(line);
        }
        return changed ? repaired : null;
    }

    private boolean validYaml(List<String> lines) {
        try {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(String.join("\n", lines));
            return true;
        } catch (InvalidConfigurationException exception) {
            return false;
        }
    }

    private static int mappingIndent(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || listEntry(line)) {
            return -1;
        }

        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return -1;
        }
        String tail = trimmed.substring(colon + 1).trim();
        if (!tail.isEmpty() && !tail.startsWith("#")) {
            return -1;
        }
        return indentation(line);
    }

    private static boolean listEntry(String line) {
        String trimmed = line.stripLeading();
        return trimmed.equals("-") || trimmed.startsWith("- ");
    }

    private static int indentation(String line) {
        int result = 0;
        while (result < line.length() && line.charAt(result) == ' ') {
            result++;
        }
        return result;
    }

    private boolean addMissingRoots(YamlConfiguration configuration, YamlConfiguration defaults) {
        boolean updated = false;
        for (String key : defaults.getKeys(false)) {
            if (configuration.contains(key)) {
                continue;
            }
            ConfigurationSection source = defaults.getConfigurationSection(key);
            if (source == null) {
                configuration.set(key, defaults.get(key));
            } else {
                ConfigurationSection target = configuration.createSection(key);
                copy(source, target);
            }
            updated = true;
        }
        return updated;
    }

    private void copy(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child == null) {
                target.set(key, source.get(key));
                continue;
            }
            ConfigurationSection section = target.createSection(key);
            copy(child, section);
        }
    }
}
