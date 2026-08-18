package ru.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.core.Core;
import ru.core.storage.PlayerPresence;
import ru.core.text.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ListCommand implements CommandExecutor {

    private final Core plugin;

    public ListCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("all"))) {
            Msg.sendCommand(plugin, sender, label, "LIST-USAGE");
            return true;
        }
        if (args.length == 1 && !sender.hasPermission("core.list.all")) {
            Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
            return true;
        }
        long fresh = System.currentTimeMillis() - 45_000L;
        plugin.data().async(() -> {
            Map<String, List<String>> online = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PlayerPresence presence : plugin.storage().presences(fresh)) {
                online.computeIfAbsent(presence.server(), ignored -> new ArrayList<>()).add(presence.name());
            }
            for (List<String> players : online.values()) {
                players.sort(String.CASE_INSENSITIVE_ORDER);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (args.length == 0) {
                    Msg.sendCommand(plugin, sender, label, "LIST-TOTAL", "%online%", String.valueOf(online.values().stream().mapToInt(List::size).sum()));
                    return;
                }
                Msg.sendCommand(plugin, sender, label, "LIST-ALL-HEADER");
                for (Map.Entry<String, List<String>> entry : online.entrySet()) {
                    Msg.sendCommand(plugin, sender, label, "LIST-ALL-LINE", "%server%", entry.getKey(),
                            "%players%", String.join(", ", entry.getValue()));
                }
            });
        });
        return true;
    }
}
