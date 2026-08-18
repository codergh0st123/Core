package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Msg;

public final class HubCommand implements CommandExecutor {

    private final Core plugin;

    public HubCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
            return true;
        }
        String lobby = plugin.proxyConnector().fallbackServer();
        if (lobby == null) {
            Msg.sendCommand(plugin, sender, label, "PLAY-UNKNOWN", "%server%", "lobby");
            return true;
        }
        if (lobby.equalsIgnoreCase(plugin.messenger().server())) {
            Msg.sendCommand(plugin, sender, label, "PLAY-ALREADY-CONNECTED", "%server%", lobby);
            return true;
        }
        if (!plugin.proxyConnector().connect(player, lobby)) {
            Msg.sendCommand(plugin, sender, label, "PLAY-CONNECT-ERROR", "%server%", lobby);
            return true;
        }
        Msg.sendCommand(plugin, sender, label, "PLAY-CONNECT", "%server%", lobby);
        return true;
    }
}
