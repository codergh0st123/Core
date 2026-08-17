package ru.core.text;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.core.Core;


public final class Msg {

    private Msg() {
    }

    public static void send(Core plugin, CommandSender sender, String path, String... replacements) {
        for (String line : plugin.configs().messages(path)) {
            sender.sendMessage(format(plugin, sender, line, replacements));
        }
    }

    public static void sendCommand(Core plugin, CommandSender sender, String label, String path,
                                   String... replacements) {
        String[] values = new String[replacements.length + 2];
        values[0] = "%label%";
        values[1] = label;
        System.arraycopy(replacements, 0, values, 2, replacements.length);
        send(plugin, sender, path, values);
    }

    public static String format(Core plugin, CommandSender sender, String line, String... replacements) {
        String text = line;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        return plugin.placeholders().apply(player, text);
    }
}
