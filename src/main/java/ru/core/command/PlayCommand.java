package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.text.Msg;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
            Msg.send(plugin, sender, "PLAYER-ONLY");
            return true;
        }
        if (args.length != 1) {
            Msg.send(plugin, sender, "PLAY-USAGE");
            return true;
        }
        String target = resolve(args[0]);
        if (target == null) {
            Msg.send(plugin, sender, "PLAY-UNKNOWN", "%server%", args[0]);
            return true;
        }
        Player player = (Player) sender;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF("Connect");
            output.writeUTF(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Ошибка отправки Connect: " + exception.getMessage());
            return true;
        }
        Msg.send(plugin, sender, "PLAY-CONNECT", "%server%", target);
        player.sendPluginMessage(plugin, "BungeeCord", buffer.toByteArray());
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
