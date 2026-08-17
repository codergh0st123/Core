package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public PlayCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
            return true;
        }
        if (args.length != 1) {
            Msg.sendCommand(plugin, sender, label, "PLAY-USAGE");
            return true;
        }
        String target = resolve(args[0]);
        if (target == null) {
            Msg.sendCommand(plugin, sender, label, "PLAY-UNKNOWN", "%server%", args[0]);
            return true;
        }
        Player player = (Player) sender;
        if (target.equalsIgnoreCase(plugin.messenger().server())) {
            Msg.sendCommand(plugin, sender, label, "PLAY-ALREADY-CONNECTED", "%server%", target);
            return true;
        }
        if (!plugin.proxyConnector().connect(player, target)) {
            Msg.sendCommand(plugin, sender, label, "PLAY-CONNECT-ERROR", "%server%", target);
            return true;
        }
        Msg.sendCommand(plugin, sender, label, "PLAY-CONNECT", "%server%", target);
        return true;
    }

    private String resolve(String name) {
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("PLAY.SERVERS");
        if (section == null) {
            return null;
        }
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) {
                return section.getString(key, key);
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("PLAY.SERVERS");
        if (section == null) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String key : section.getKeys(false)) {
            if (key.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(key);
            }
        }
        return result;
    }
}
