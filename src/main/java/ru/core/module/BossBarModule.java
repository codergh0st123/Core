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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BossBarModule {

    private final Core plugin;
    private final Map<UUID, Map<String, BossBar>> bars = new HashMap<>();
    private BukkitTask task;

    public BossBarModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("BOSSBARS.ENABLED", false)) {
            return;
        }
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

    private void update() {
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("BOSSBARS.BARS");
        if (section == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, BossBar> owned = bars.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
            for (String key : section.getKeys(false)) {
                ConfigurationSection bar = section.getConfigurationSection(key);
                if (bar == null) {
                    continue;
                }
                if (!bar.getBoolean("ENABLED", true)) {
                    BossBar disabled = owned.remove(key);
                    if (disabled != null) {
                        disabled.removeAll();
                    }
                    continue;
                }
                List<String> titles = bar.getStringList("TITLE");
                String title = titles.isEmpty() ? "" : plugin.placeholders().apply(player, titles.get(0));
                BossBar current = owned.get(key);
                if (current == null) {
                    current = Bukkit.createBossBar(title, color(bar.getString("COLOR", "WHITE")), style(bar.getString("STYLE", "SOLID")));
                    current.addPlayer(player);
                    owned.put(key, current);
                } else if (!current.getTitle().equals(title)) {
                    current.setTitle(title);
                }
                BarColor color = color(bar.getString("COLOR", "WHITE"));
                if (current.getColor() != color) {
                    current.setColor(color);
                }
                BarStyle style = style(bar.getString("STYLE", "SOLID"));
                if (current.getStyle() != style) {
                    current.setStyle(style);
                }
                double progress = Math.max(0.0D, Math.min(1.0D, bar.getDouble("PROGRESS", 1.0D)));
                if (current.getProgress() != progress) {
                    current.setProgress(progress);
                }
                if (!current.isVisible()) {
                    current.setVisible(true);
                }
            }
            for (String key : new ArrayList<>(owned.keySet())) {
                if (!section.contains(key)) {
                    BossBar removed = owned.remove(key);
                    if (removed != null) {
                        removed.removeAll();
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
