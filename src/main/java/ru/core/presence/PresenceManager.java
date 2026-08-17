package ru.core.presence;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.storage.PlayerPresence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PresenceManager {

    private static final long FRESH_MILLIS = 45_000L;
    private static final long CLEANUP_MILLIS = 300_000L;

    private final Core plugin;
    private final ExecutorService executor;
    private volatile long session;
    private int refreshes;
    private BukkitTask task;

    public PresenceManager(Core plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Core-Presence");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        stop();
        long currentSession = ++session;
        refreshes = 0;
        refresh(currentSession);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(currentSession), 600L, 600L);
    }

    public void reload() {
        start();
    }

    public void join(Player player) {
        PlayerPresence presence = presence(player);
        execute(() -> plugin.storage().updatePresence(presence.uuid(), presence.name(), presence.server(), presence.updated()));
    }

    public void quit(Player player) {
        String server = plugin.messenger().server();
        execute(() -> plugin.storage().removePresence(player.getUniqueId(), server));
    }

    public void find(String name, Consumer<PlayerPresence> callback) {
        long currentSession = session;
        long updatedAfter = System.currentTimeMillis() - FRESH_MILLIS;
        execute(() -> {
            if (!active(currentSession)) {
                return;
            }
            PlayerPresence presence = plugin.storage().findPresence(name, updatedAfter);
            if (!active(currentSession)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active(currentSession)) {
                    callback.accept(presence);
                }
            });
        });
    }

    public void stop() {
        session++;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void refresh(long currentSession) {
        if (!active(currentSession)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (++refreshes >= 10) {
            refreshes = 0;
            execute(() -> plugin.storage().cleanupPresence(now - CLEANUP_MILLIS));
        }
        List<PlayerPresence> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(presence(player, now));
        }
        if (players.isEmpty()) {
            return;
        }
        execute(() -> {
            if (active(currentSession)) {
                plugin.storage().updatePresences(players);
            }
        });
    }

    private PlayerPresence presence(Player player) {
        return presence(player, System.currentTimeMillis());
    }

    private PlayerPresence presence(Player player, long updated) {
        return new PlayerPresence(player.getUniqueId(), player.getName(), plugin.messenger().server(), updated);
    }

    private boolean active(long currentSession) {
        return plugin.isEnabled() && session == currentSession;
    }

    private void execute(Runnable runnable) {
        if (executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }
}
