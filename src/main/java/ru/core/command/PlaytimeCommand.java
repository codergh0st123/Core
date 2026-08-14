package ru.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.core.Core;
import ru.core.storage.Profile;
import ru.core.text.Msg;
import ru.core.text.TimeFormat;

public final class PlaytimeCommand implements CommandExecutor {

    private final Core plugin;

    public PlaytimeCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.send(plugin, sender, "PLAYER-ONLY");
            return true;
        }
        Profile profile = plugin.data().profile((Player) sender);
        if (profile == null) {
            Msg.send(plugin, sender, "PROFILE-LOADING");
            return true;
        }
        for (String line : plugin.configs().messages("PLAYTIME")) {
            sender.sendMessage(Msg.format(plugin, sender, TimeFormat.fill(plugin, line, profile.playtime())));
        }
        return true;
    }
}
