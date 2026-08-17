package ru.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.storage.PlayerPresence;
import ru.core.text.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FindCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public FindCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            Msg.sendCommand(plugin, sender, label, "FIND-USAGE");
            return true;
        }
        String name = args[0];
        plugin.presence().find(name, presence -> sendResult(sender, label, name, presence));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(player.getName());
            }
        }
        return result;
    }

    private void sendResult(CommandSender sender, String label, String name, PlayerPresence presence) {
        if (sender instanceof Player player && !player.isOnline()) {
            return;
        }
        if (presence == null) {
            Msg.sendCommand(plugin, sender, label, "FIND-NOT-FOUND", "%player%", name);
            return;
        }
        Msg.sendCommand(plugin, sender, label, "FIND-FOUND", "%player%", presence.name(), "%server%", presence.server());
    }
}
