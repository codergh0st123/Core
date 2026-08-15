package ru.core.animation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.core.Core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnimationManager {

    private final Core plugin;
    private Map<String, TextAnimation> animations = Map.of();

    public AnimationManager(Core plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration configuration) {
        Map<String, TextAnimation> loaded = new LinkedHashMap<>();
        for (String name : configuration.getKeys(false)) {
            ConfigurationSection section = configuration.getConfigurationSection(name);
            if (section == null) {
                plugin.getLogger().warning("Анимация " + name + " должна быть секцией в animations.yml.");
                continue;
            }
            List<String> texts = section.getStringList("TEXTS");
            if (texts.isEmpty()) {
                plugin.getLogger().warning("Анимация " + name + " не содержит TEXTS в animations.yml.");
                continue;
            }
            long interval = Math.max(1L, section.getLong("CHANGE-INTERVAL", 50L));
            loaded.put(name.toUpperCase(Locale.ROOT), new TextAnimation(System.nanoTime(), interval, texts));
        }
        animations = Map.copyOf(loaded);
    }

    public String text(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        TextAnimation animation = animations.get(name.toUpperCase(Locale.ROOT));
        return animation == null ? null : animation.text();
    }
}
