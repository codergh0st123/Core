package ru.core.net;

import org.bukkit.entity.Player;
import ru.core.Core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProxyConnector {

    private final Core plugin;

    public ProxyConnector(Core plugin) {
        this.plugin = plugin;
    }

    public boolean connect(Player player, String server) {
        if (server == null || server.isBlank()) {
            return false;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF("Connect");
            output.writeUTF(server);
        } catch (IOException exception) {
            plugin.getLogger().warning("Ошибка отправки Connect: " + exception.getMessage());
            return false;
        }
        player.sendPluginMessage(plugin, "BungeeCord", buffer.toByteArray());
        return true;
    }

    public String fallbackServer() {
        if (!plugin.configs().config().getBoolean("PLAY-FALLBACK.ENABLED", true)) {
            return null;
        }
        String lobby = plugin.configs().config().getString("PLAY-FALLBACK.SERVER", "");
        if (lobby.isBlank() || lobby.equalsIgnoreCase(plugin.messenger().server())) {
            return null;
        }
        return lobby;
    }

    public boolean fallback(Player player) {
        String lobby = fallbackServer();
        return lobby != null && connect(player, lobby);
    }
}
