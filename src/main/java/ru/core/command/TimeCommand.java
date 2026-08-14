package ru.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.storage.Profile;
import ru.core.text.Msg;
import ru.core.text.TimeFormat;
import ru.core.text.TimeParse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TimeCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public TimeCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.time")) {
            Msg.send(plugin, sender, "NO-PERMISSION");
            return true;
        }
        if (args.length != 3 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("set"))) {
            Msg.send(plugin, sender, "TIME-USAGE");
            return true;
        }
        long seconds = TimeParse.seconds(args[2]);
        if (seconds < 0L) {
            Msg.send(plugin, sender, "TIME-FORMAT");
            return true;
        }
        boolean add = args[0].equalsIgnoreCase("add");
        String name = args[1];
        String formatted = TimeFormat.compact(plugin, seconds);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            Profile profile = plugin.data().profile(online);
            if (profile == null) {
                Msg.send(plugin, sender, "PROFILE-LOADING");
                return true;
            }
            if (add) {
                profile.addPlaytime(seconds);
            } else {
                profile.playtime(seconds);
            }
            plugin.data().async(() -> plugin.storage().save(profile));
            Msg.send(plugin, sender, add ? "TIME-ADD" : "TIME-SET", "%player%", online.getName(), "%time%", formatted);
            return true;
        }
        plugin.data().async(() -> {
            boolean done = add ? plugin.storage().addTime(name, seconds) : plugin.storage().setTime(name, seconds);
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!done) {
                    Msg.send(plugin, sender, "TIME-UNKNOWN", "%player%", name);
                    return;
                }
                Msg.send(plugin, sender, add ? "TIME-ADD" : "TIME-SET", "%player%", name, "%time%", formatted);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("core.time")) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String action : new String[]{"add", "set"}) {
                if (action.startsWith(prefix)) {
                    result.add(action);
                }
            }
            return result;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
        }
        return result;
    }
}
