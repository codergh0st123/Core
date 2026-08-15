package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.board.PlayerBoard;

import java.util.List;

public final class NameTagModule {

    private final Core plugin;
    private BukkitTask task;

    public NameTagModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("NAMETAG.ENABLED", false)) {
            return;
        }
        long period = Math.max(1L, plugin.configs().config().getLong("NAMETAG.UPDATE", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeBelow();
        }
    }

    private void update() {
        boolean health = "HEALTH".equalsIgnoreCase(plugin.configs().config().getString("NAMETAG.MODE", "PING"));
        List<String> lines = plugin.configs().config().getStringList("NAMETAG.DISPLAY");
        String raw = lines.isEmpty() ? "" : lines.get(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = plugin.boards().get(player);
            if (board == null) {
                continue;
            }
            board.below(plugin.placeholders().apply(player, raw), health);
            if (health) {
                continue;
            }
            for (Player target : Bukkit.getOnlinePlayers()) {
                board.value(target.getName(), target.getPing());
            }
        }
        plugin.groups().updateTags();
    }
}
