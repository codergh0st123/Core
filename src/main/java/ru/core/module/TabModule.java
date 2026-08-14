package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TabModule {

    private final Core plugin;
    private final Map<UUID, TabText> lastText = new HashMap<>();
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
        lastText.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter("", "");
        }
    }

    public void remove(Player player) {
        if (player != null) {
            lastText.remove(player.getUniqueId());
        }
    }

    private void update() {
        List<String> header = plugin.configs().config().getStringList("TAB.HEADER");
        List<String> footer = plugin.configs().config().getStringList("TAB.FOOTER");
        for (Player player : Bukkit.getOnlinePlayers()) {
            TabText text = new TabText(
                    String.join("\n", plugin.placeholders().apply(player, header)),
                    String.join("\n", plugin.placeholders().apply(player, footer))
            );
            if (text.equals(lastText.get(player.getUniqueId()))) {
                continue;
            }
            player.setPlayerListHeaderFooter(text.header, text.footer);
            lastText.put(player.getUniqueId(), text);
        }
    }

    private static final class TabText {

        private final String header;
        private final String footer;

        private TabText(String header, String footer) {
            this.header = header;
            this.footer = footer;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TabText)) {
                return false;
            }
            TabText other = (TabText) object;
            return header.equals(other.header) && footer.equals(other.footer);
        }

        @Override
        public int hashCode() {
            int result = header.hashCode();
            return 31 * result + footer.hashCode();
        }
    }
}
