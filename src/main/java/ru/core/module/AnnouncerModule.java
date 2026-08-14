package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.ArrayList;
import java.util.List;

public final class AnnouncerModule {

    private final Core plugin;
    private BukkitTask task;
    private int index;

    public AnnouncerModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("ANNOUNCER.ENABLED", false)) {
            return;
        }
        long interval = Math.max(1L, plugin.configs().config().getLong("ANNOUNCER.INTERVAL", 300L)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcast, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        index = 0;
    }

    private void broadcast() {
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("ANNOUNCER.MESSAGES");
        if (section == null) {
            return;
        }
        List<String> keys = sorted(section);
        if (keys.isEmpty()) {
            return;
        }
        if (index >= keys.size()) {
            index = 0;
        }
        List<String> lines = section.getStringList(keys.get(index));
        index++;
        if (lines.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String line : plugin.placeholders().apply(player, lines)) {
                player.sendMessage(line);
            }
        }
    }

    private List<String> sorted(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        boolean numbers = true;
        for (String key : keys) {
            if (!key.matches("\\d+")) {
                numbers = false;
                break;
            }
        }
        if (numbers) {
            keys.sort((first, second) -> Long.compare(Long.parseLong(first), Long.parseLong(second)));
            return keys;
        }
        keys.sort(String::compareTo);
        return keys;
    }
}
