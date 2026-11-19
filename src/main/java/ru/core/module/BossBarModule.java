package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossBarModule {

    private final Core plugin;
    private final Map<UUID, Map<String, BossBar>> bars = new HashMap<>();
    private BukkitTask task;
    private List<Definition> definitions;

    public BossBarModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("BOSSBARS.ENABLED", false)) {
            return;
        }
        definitions = definitions();
        long period = Math.max(1L, plugin.configs().config().getLong("BOSSBARS.UPDATE", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map<String, BossBar> owned : bars.values()) {
            for (BossBar bar : owned.values()) {
                bar.removeAll();
            }
        }
        bars.clear();
        definitions = null;
    }

    public void remove(Player player) {
        Map<String, BossBar> owned = bars.remove(player.getUniqueId());
        if (owned == null) {
            return;
        }
        for (BossBar bar : owned.values()) {
            bar.removeAll();
        }
    }

    private record Definition(String key, boolean enabled, String title, BarColor color, BarStyle style, double progress) {
    }

    private List<Definition> definitions() {
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("BOSSBARS.BARS");
        if (section == null) {
            return null;
        }
        List<Definition> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection bar = section.getConfigurationSection(key);
            if (bar == null) {
                continue;
            }
            List<String> titles = bar.getStringList("TITLE");
            result.add(new Definition(key,
                    bar.getBoolean("ENABLED", true),
                    titles.isEmpty() ? "" : titles.get(0),
                    color(bar.getString("COLOR", "WHITE")),
                    style(bar.getString("STYLE", "SOLID")),
                    Math.max(0.0D, Math.min(1.0D, bar.getDouble("PROGRESS", 1.0D)))));
        }
        return result;
    }

    private void update() {
        if (definitions == null) {
            definitions = definitions();
        }
        List<Definition> current = definitions;
        if (current == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        for (Definition definition : current) {
            known.add(definition.key());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, BossBar> owned = bars.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
            for (Definition definition : current) {
                if (!definition.enabled()) {
                    BossBar disabled = owned.remove(definition.key());
                    if (disabled != null) {
                        disabled.removeAll();
                    }
                    continue;
                }
                String title = definition.title().isEmpty() ? "" : plugin.placeholders().apply(player, definition.title());
                BossBar bar = owned.get(definition.key());
                if (bar == null) {
                    bar = Bukkit.createBossBar(title, definition.color(), definition.style());
                    bar.addPlayer(player);
                    owned.put(definition.key(), bar);
                } else if (!bar.getTitle().equals(title)) {
                    bar.setTitle(title);
                }
                if (bar.getColor() != definition.color()) {
                    bar.setColor(definition.color());
                }
                if (bar.getStyle() != definition.style()) {
                    bar.setStyle(definition.style());
                }
                if (bar.getProgress() != definition.progress()) {
                    bar.setProgress(definition.progress());
                }
                if (!bar.isVisible()) {
                    bar.setVisible(true);
                }
            }
            if (owned.size() > known.size() || !known.containsAll(owned.keySet())) {
                Iterator<Map.Entry<String, BossBar>> iterator = owned.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, BossBar> entry = iterator.next();
                    if (!known.contains(entry.getKey())) {
                        entry.getValue().removeAll();
                        iterator.remove();
                    }
                }
            }
        }
    }

    private BarColor color(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarColor.WHITE;
        }
    }

    private BarStyle style(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarStyle.SOLID;
        }
    }
}
