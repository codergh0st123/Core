package ru.core.module;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.List;

public final class ScreenTextModule {

    private final Core plugin;
    private BukkitTask task;

    public ScreenTextModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("SCREEN-TEXT.ENABLED", false)) {
            return;
        }
        long period = Math.max(1L, plugin.configs().config().getLong("SCREEN-TEXT.UPDATE", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> update(period), 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        boolean title = "TITLE".equalsIgnoreCase(plugin.configs().config().getString("SCREEN-TEXT.TYPE", "ACTIONBAR"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (title) {
                player.resetTitle();
                continue;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
        }
    }

    private void update(long period) {
        List<String> text = plugin.configs().config().getStringList("SCREEN-TEXT.TEXT");
        List<String> subtitle = plugin.configs().config().getStringList("SCREEN-TEXT.SUBTITLE");
        if (text.isEmpty()) {
            return;
        }
        boolean title = "TITLE".equalsIgnoreCase(plugin.configs().config().getString("SCREEN-TEXT.TYPE", "ACTIONBAR"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            String line = plugin.placeholders().apply(player, text.get(0));
            if (title) {
                String second = subtitle.isEmpty() ? "" : plugin.placeholders().apply(player, subtitle.get(0));
                player.sendTitle(line, second, 0, (int) period + 10, 0);
                continue;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(line));
        }
    }
}
