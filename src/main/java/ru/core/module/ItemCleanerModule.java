package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ItemCleanerModule {

    private final Core plugin;
    private BukkitTask task;
    private Set<Integer> warningSeconds = Set.of();
    private List<String> warningMessages = List.of();
    private List<String> clearedMessages = List.of();
    private int checkInterval;
    private int minimumItems;
    private int clearDelay;
    private int secondsUntilCheck;
    private int secondsUntilClear;
    private boolean clearing;

    public ItemCleanerModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("ITEM-CLEANER.ENABLED", false)) {
            return;
        }
        checkInterval = Math.max(1, plugin.configs().config().getInt("ITEM-CLEANER.CHECK-INTERVAL", 300));
        minimumItems = Math.max(1, plugin.configs().config().getInt("ITEM-CLEANER.MINIMUM-ITEMS", 25));
        clearDelay = Math.max(0, plugin.configs().config().getInt("ITEM-CLEANER.CLEAR-DELAY", 30));
        warningSeconds = new HashSet<>(plugin.configs().config().getIntegerList("ITEM-CLEANER.WARNING-SECONDS"));
        warningMessages = plugin.configs().config().getStringList("ITEM-CLEANER.MESSAGES.WARNING");
        clearedMessages = plugin.configs().config().getStringList("ITEM-CLEANER.MESSAGES.CLEARED");
        secondsUntilCheck = checkInterval;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        warningSeconds = Set.of();
        warningMessages = List.of();
        clearedMessages = List.of();
        secondsUntilCheck = 0;
        secondsUntilClear = 0;
        clearing = false;
    }

    private void tick() {
        if (clearing) {
            tickCleanup();
            return;
        }
        secondsUntilCheck--;
        if (secondsUntilCheck > 0) {
            return;
        }
        secondsUntilCheck = checkInterval;
        if (countItems() < minimumItems) {
            return;
        }
        startCleanup();
    }

    private void startCleanup() {
        secondsUntilClear = clearDelay;
        clearing = true;
        if (secondsUntilClear == 0) {
            clearItems();
            return;
        }
        sendWarning(secondsUntilClear);
    }

    private void tickCleanup() {
        secondsUntilClear--;
        if (secondsUntilClear <= 0) {
            clearItems();
            return;
        }
        sendWarning(secondsUntilClear);
    }

    private int countItems() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    count++;
                }
            }
        }
        return count;
    }

    private void clearItems() {
        int cleared = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Item item)) {
                    continue;
                }
                item.remove();
                cleared++;
            }
        }
        clearing = false;
        secondsUntilClear = 0;
        if (cleared > 0) {
            broadcast(clearedMessages, 0, cleared);
        }
    }

    private void sendWarning(int seconds) {
        if (warningSeconds.contains(seconds)) {
            broadcast(warningMessages, seconds, 0);
        }
    }

    private void broadcast(List<String> messages, int seconds, int cleared) {
        if (messages.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String line : messages) {
                String text = line
                        .replace("%SECONDS%", String.valueOf(seconds))
                        .replace("%COUNT%", String.valueOf(cleared));
                player.sendMessage(plugin.placeholders().apply(player, text));
            }
        }
    }
}
