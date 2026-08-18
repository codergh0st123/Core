package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.storage.Profile;
import ru.core.text.Msg;

import java.util.ArrayList;
import java.util.List;

public final class ConsoleCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public ConsoleCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.console")) {
            Msg.sendCommand(plugin, sender, label, "NO-PERMISSION");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
            return true;
        }
        if (args.length != 1) {
            Msg.sendCommand(plugin, sender, label, "CONSOLE-USAGE");
            return true;
        }
        Profile profile = plugin.data().profile(player);
        if (profile == null) {
            Msg.sendCommand(plugin, sender, label, "PROFILE-LOADING");
            return true;
        }
        if (args[0].equalsIgnoreCase("on")) {
            profile.commandConsole(true);
            plugin.data().async(() -> plugin.storage().save(profile));
            Msg.sendCommand(plugin, sender, label, "CONSOLE-ON");
            return true;
        }
        if (args[0].equalsIgnoreCase("off")) {
            profile.commandConsole(false);
            plugin.data().async(() -> plugin.storage().save(profile));
            Msg.sendCommand(plugin, sender, label, "CONSOLE-OFF");
            return true;
        }
        Msg.sendCommand(plugin, sender, label, "CONSOLE-USAGE");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission("core.console")) {
            return result;
        }
        for (String option : List.of("on", "off")) {
            if (option.startsWith(args[0].toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
