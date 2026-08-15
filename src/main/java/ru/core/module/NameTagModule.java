package ru.core.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.board.PlayerBoard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NameTagModule {

    private final Core plugin;
    private final Set<UUID> pendingHealth = new HashSet<>();
    private BukkitTask task;

    public NameTagModule(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configs().config().getBoolean("NAMETAG.ENABLED", false)) {
            return;
        }
        long period = Math.max(1L, plugin.configs().config().getLong("NAMETAG.UPDATE", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pendingHealth.clear();
        for (PlayerBoard board : plugin.boards().all()) {
            board.removeBelow();
        }
    }

    public void queueHealthUpdate(Player player) {
        if (!healthMode() || player == null || !pendingHealth.add(player.getUniqueId())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingHealth.remove(uuid);
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                updateHealth(target);
            }
        });
    }

    private void update() {
        boolean health = healthMode();
        List<String> lines = plugin.configs().config().getStringList("NAMETAG.DISPLAY");
        String raw = lines.isEmpty() ? "" : lines.get(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = plugin.boards().get(player);
            if (board == null) {
                continue;
            }
            board.below(plugin.placeholders().apply(player, raw), health);
            for (Player target : Bukkit.getOnlinePlayers()) {
                int value = health ? health(target) : target.getPing();
                board.value(target.getName(), value);
            }
        }
        plugin.groups().updateTags();
    }

    private void updateHealth(Player target) {
        if (!healthMode()) {
            return;
        }
        int value = health(target);
        for (PlayerBoard board : plugin.boards().all()) {
            board.value(target.getName(), value);
        }
    }

    private boolean healthMode() {
        return "HEALTH".equalsIgnoreCase(plugin.configs().config().getString("NAMETAG.MODE", "PING"));
    }

    private int health(Player player) {
        return Math.max(0, (int) Math.ceil(player.getHealth()));
    }
}
