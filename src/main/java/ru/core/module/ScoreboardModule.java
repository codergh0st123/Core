package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.board.PlayerBoard;

import java.util.List;

public final class ScoreboardModule {

    private final Core plugin;
    private BukkitTask task;
    private int frame;

    public ScoreboardModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("SCOREBOARD.ENABLED", false)) {
            return;
        }
        long period = Math.max(1L, plugin.configs().config().getLong("SCOREBOARD.UPDATE", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        frame = 0;
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeSidebar();
        }
    }

    private void update() {
        List<String> titles = plugin.configs().config().getStringList("SCOREBOARD.TITLE");
        List<String> content = plugin.configs().config().getStringList("SCOREBOARD.LINES");
        if (titles.isEmpty() || content.isEmpty()) {
            return;
        }
        String title = titles.get(frame % titles.size());
        frame++;
        if (frame >= titles.size()) {
            frame = 0;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = plugin.boards().get(player);
            if (board == null) {
                continue;
            }
            board.sidebar(plugin.placeholders().apply(player, title), plugin.placeholders().apply(player, content));
        }
    }
}
