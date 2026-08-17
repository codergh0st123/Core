package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Msg;

public final class ServerCommand implements CommandExecutor {

    private final Core plugin;

    public ServerCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.server")) {
            Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
            return true;
        }
        if (args.length != 2) {
            Msg.sendCommand(plugin, sender, label, "SERVER-USAGE");
            return true;
        }
        if (!plugin.proxyConnector().connectOther(player, args[1], args[0])) {
            Msg.sendCommand(plugin, sender, label, "SERVER-ERROR");
            return true;
        }
        Msg.sendCommand(plugin, sender, label, "SERVER-SENT", "%player%", args[1], "%server%", args[0]);
        return true;
    }
}
