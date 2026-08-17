package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.core.Core;
import ru.core.net.Messenger;
import ru.core.text.Msg;

public final class AlertCommand implements CommandExecutor {

    private final Core plugin;

    public AlertCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.alert")) {
            Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
            return true;
        }
        if (args.length == 0) {
            Msg.sendCommand(plugin, sender, label, "ALERT-USAGE");
            return true;
        }
        plugin.messenger().broadcast(Messenger.ALERT, sender.getName(), String.join(" ", args));
        return true;
    }
}
