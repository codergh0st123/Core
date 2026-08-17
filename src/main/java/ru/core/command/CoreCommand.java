package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Msg;

import java.util.ArrayList;
import java.util.List;

public final class CoreCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public CoreCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
                return true;
            }
            plugin.menu().open((Player) sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("core.reload")) {
                Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
                return true;
            }
            plugin.reloadAll();
            Msg.sendCommand(plugin, sender, label, "RELOAD");
            return true;
        }
        Msg.sendCommand(plugin, sender, label, "CORE-USAGE");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("core.reload") && "reload".startsWith(args[0].toLowerCase())) {
            result.add("reload");
        }
        return result;
    }
}
