package ru.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import ru.core.Core;

import java.lang.reflect.Method;
import java.util.Map;

public final class Aliases {

    private Aliases() {
    }

    public static void release(Core plugin, PluginCommand command, String... aliases) {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            SimpleCommandMap map = (SimpleCommandMap) method.invoke(Bukkit.getServer());
            Map<String, Command> known = (Map<String, Command>) map.getCommands();
            for (String alias : aliases) {
                if (known.get(alias.toLowerCase()) == command) {
                    known.remove(alias.toLowerCase());
                }
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Не удалось освободить алиасы команды: " + exception.getMessage());
        }
    }

    public static void force(Core plugin, PluginCommand command, String... aliases) {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            SimpleCommandMap map = (SimpleCommandMap) method.invoke(Bukkit.getServer());
            Map<String, Command> known = (Map<String, Command>) map.getCommands();
            for (String alias : aliases) {
                known.put(alias.toLowerCase(), command);
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Не удалось перехватить алиасы команды: " + exception.getMessage());
        }
    }
}
