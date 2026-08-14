package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.List;

public final class TabModule {

    private final Core plugin;
    private BukkitTask task;

    public TabModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("TAB.ENABLED", false)) {
            return;
        }
        long period = Math.max(1L, plugin.configs().config().getLong("TAB.UPDATE", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter("", "");
        }
    }

    private void update() {
        List<String> header = plugin.configs().config().getStringList("TAB.HEADER");
        List<String> footer = plugin.configs().config().getStringList("TAB.FOOTER");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter(String.join("\n", plugin.placeholders().apply(player, header)),
                    String.join("\n", plugin.placeholders().apply(player, footer)));
        }
    }
}
