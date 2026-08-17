package ru.core.resourcepack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import ru.core.Core;
import ru.core.text.Msg;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourcePackManager {

    private final Core plugin;
    private volatile long session;
    private final Set<UUID> kicks = ConcurrentHashMap.newKeySet();
    private volatile UUID packId;

    public ResourcePackManager(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
    }

    public void reload() {
        long currentSession = ++session;
        packId = UUID.randomUUID();
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendLater(player, currentSession);
        }
    }

    public void stop() {
        session++;
        packId = null;
        kicks.clear();
    }

    public void join(Player player) {
        if (enabled()) {
            sendLater(player, session);
        }
    }

    public void handle(PlayerResourcePackStatusEvent event) {
        if (!required() || !event.getID().equals(packId) || !failed(event.getStatus())) {
            return;
        }
        Player player = event.getPlayer();
        String reason = event.getStatus().name().toLowerCase(Locale.ROOT);
        List<String> lines = plugin.placeholders().apply(player, plugin.configs().messages("RESOURCE-PACK-KICK"));
        kicks.add(player.getUniqueId());
        player.kickPlayer(String.join("\n", lines).replace("%reason%", reason));
        Bukkit.getScheduler().runTaskLater(plugin, () -> kicks.remove(player.getUniqueId()), 1L);
    }

    public boolean isResourcePackKick(Player player) {
        return kicks.contains(player.getUniqueId());
    }

    private void sendLater(Player player, long currentSession) {
        long delay = Math.max(0L, plugin.configs().config().getLong("RESOURCE-PACK.DELAY", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || session != currentSession || !enabled()) {
                return;
            }
            String url = plugin.configs().config().getString("RESOURCE-PACK.URL", "");
            if (url.isBlank()) {
                plugin.getLogger().warning("Ресурс-пак включён, но URL не указан.");
                return;
            }
            String prompt = plugin.configs().config().getString("RESOURCE-PACK.PROMPT", "");
            player.setResourcePack(packId, url, hash(), plugin.placeholders().apply(player, prompt), required());
        }, delay);
    }

    private boolean enabled() {
        return plugin.configs().config().getBoolean("RESOURCE-PACK.ENABLED", false);
    }

    private boolean required() {
        return enabled() && plugin.configs().config().getBoolean("RESOURCE-PACK.REQUIRED", true);
    }

    private byte[] hash() {
        String value = plugin.configs().config().getString("RESOURCE-PACK.HASH", "").replace(" ", "");
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("[0-9a-fA-F]{40}")) {
            plugin.getLogger().warning("HASH ресурс-пака должен содержать SHA-1 из 40 символов.");
            return null;
        }
        byte[] result = new byte[20];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private boolean failed(PlayerResourcePackStatusEvent.Status status) {
        switch (status) {
            case DECLINED:
            case FAILED_DOWNLOAD:
            case INVALID_URL:
            case FAILED_RELOAD:
            case DISCARDED:
                return true;
            default:
                return false;
        }
    }
}
