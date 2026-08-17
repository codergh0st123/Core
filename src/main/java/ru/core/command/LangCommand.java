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
import java.util.Locale;

public final class LangCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public LangCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.sendCommand(plugin, sender, label, "PLAYER-ONLY");
            return true;
        }
        if (args.length != 1) {
            Msg.sendCommand(plugin, sender, label, "LANG-USAGE");
            Msg.sendCommand(plugin, sender, label, "LANG-LIST", "%langs%", plugin.placeholders().languageList());
            return true;
        }
        String code = args[0].toUpperCase(Locale.ROOT);
        if (!plugin.placeholders().hasLanguage(code)) {
            Msg.sendCommand(plugin, sender, label, "LANG-UNKNOWN", "%lang%", args[0]);
            return true;
        }
        Profile profile = plugin.data().profile((Player) sender);
        if (profile == null) {
            Msg.sendCommand(plugin, sender, label, "PROFILE-LOADING");
            return true;
        }
        profile.language(code);
        plugin.data().async(() -> plugin.storage().save(profile));
        Msg.sendCommand(plugin, sender, label, "LANG-CHANGED", "%lang%", code);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        String prefix = args[0].toUpperCase(Locale.ROOT);
        for (String code : plugin.configs().languages()) {
            if (code.startsWith(prefix)) {
                result.add(code);
            }
        }
        return result;
    }
}
