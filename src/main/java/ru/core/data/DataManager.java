package ru.core.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.storage.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DataManager {

    private final Core plugin;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final ProfileCache cache = new ProfileCache();
    private final ExecutorService executor;
    private BukkitTask timeTask;
    private BukkitTask saveTask;
    private BukkitTask cacheTask;

    public DataManager(Core plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Core-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        configureCache();
        timeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        startSaveTask();
        startCacheTask();
    }

    public void reload() {
        cancel(saveTask);
        saveTask = null;
        cancel(cacheTask);
        cacheTask = null;
        configureCache();
        startSaveTask();
        startCacheTask();
    }

    public void async(Runnable task) {
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(task);
    }

    public Profile profile(Player player) {
        return player == null ? null : profiles.get(player.getUniqueId());
    }

    public void join(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Profile cached = cache.take(uuid, System.currentTimeMillis());
        if (cached != null) {
            cached.name(name);
            profiles.put(uuid, cached);
            return;
        }
        String language = plugin.configs().defaultLanguage();
        async(() -> {
            Profile profile = plugin.storage().load(uuid, name, language);
            profile.name(name);
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) {
                    return;
                }
                profiles.put(uuid, profile);
            });
        });
    }

    public void quit(Player player) {
        Profile profile = profiles.remove(player.getUniqueId());
        if (profile == null) {
            return;
        }
        cache.put(profile, System.currentTimeMillis());
        async(() -> plugin.storage().save(profile));
    }

    public void invalidate(String name) {
        cache.remove(name);
    }

    private void configureCache() {
        boolean enabled = plugin.configs().config().getBoolean("DATABASE.CACHE.ENABLED", true);
        int maximum = Math.max(1, plugin.configs().config().getInt("DATABASE.CACHE.MAX-ENTRIES", 250));
        long ttl = Math.max(30L, plugin.configs().config().getLong("DATABASE.CACHE.TTL-SECONDS", 300L));
        cache.configure(enabled, maximum, ttl * 1000L);
    }

    private void startSaveTask() {
        long interval = Math.max(30L, plugin.configs().config().getLong("DATABASE.SAVE-INTERVAL", 300L));
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveOnline, interval * 20L, interval * 20L);
    }

    private void startCacheTask() {
        if (!plugin.configs().config().getBoolean("DATABASE.CACHE.ENABLED", true)) {
            return;
        }
        long interval = Math.max(15L, plugin.configs().config().getLong("DATABASE.CACHE.CLEANUP-INTERVAL", 60L));
        cacheTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> cache.cleanup(System.currentTimeMillis()), interval * 20L, interval * 20L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Profile profile = profiles.get(player.getUniqueId());
            if (profile != null) {
                profile.addPlaytime(1L);
            }
        }
    }

    private void saveOnline() {
        if (profiles.isEmpty()) {
            return;
        }
        List<Profile> snapshot = new ArrayList<>(profiles.values());
        async(() -> plugin.storage().saveAll(snapshot));
    }

    public void shutdown() {
        cancel(timeTask);
        timeTask = null;
        cancel(saveTask);
        saveTask = null;
        cancel(cacheTask);
        cacheTask = null;
        List<Profile> pending = new ArrayList<>(profiles.values());
        profiles.clear();
        cache.clear();
        if (!executor.isShutdown()) {
            executor.execute(() -> plugin.storage().saveAll(pending));
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(15L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
