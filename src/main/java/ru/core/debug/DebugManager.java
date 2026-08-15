package ru.core.debug;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.core.Core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugManager {

    private final Core plugin;
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();

    public DebugManager(Core plugin) {
        this.plugin = plugin;
    }

    public boolean enable(Player player) {
        return subscribers.add(player.getUniqueId());
    }

    public boolean disable(Player player) {
        return subscribers.remove(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        subscribers.remove(uuid);
    }

    public void clear() {
        subscribers.clear();
    }

    public void log(String source, String message) {
        if (!plugin.configs().config().getBoolean("DEBUG.ENABLED", true)) {
            return;
        }
        if (!plugin.configs().config().getBoolean("DEBUG.SOURCES." + source, true) || subscribers.isEmpty()) {
            return;
        }
        String text = "&8[&cDebug&8] &7[&f" + source + "&7] &f" + message;
        Bukkit.getScheduler().runTask(plugin, () -> send(text));
    }

    private void send(String text) {
        for (UUID uuid : subscribers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                subscribers.remove(uuid);
                continue;
            }
            if (!player.hasPermission("core.log")) {
                subscribers.remove(uuid);
                continue;
            }
            player.sendMessage(plugin.placeholders().apply(player, text));
        }
    }
}
