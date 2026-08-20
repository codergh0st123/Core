package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.net.Messenger;
import ru.core.text.Msg;

public final class PremiumChatCommand implements CommandExecutor {

    private final Core plugin;

    public PremiumChatCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.chat.premium")) {
            Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
            return true;
        }
        if (args.length == 0) {
            Msg.sendCommand(plugin, sender, label, "PREMIUM-USAGE");
            return true;
        }
        if (sender instanceof Player player) {
            plugin.messenger().broadcast(Messenger.PREMIUM, player, String.join(" ", args));
        } else {
            plugin.messenger().broadcast(Messenger.PREMIUM, sender.getName(), String.join(" ", args));
        }
        return true;
    }
}
