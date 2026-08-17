package ru.core.wipe;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.net.Messenger;
import ru.core.storage.WipeState;
import ru.core.text.TimeFormat;
import ru.core.text.TimeParse;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class WipeManager {

    private final Core plugin;
    private final ExecutorService executor;
    private final ConcurrentMap<String, Wipe> wipes = new ConcurrentHashMap<>();
    private volatile long session;
    private BukkitTask task;

    public WipeManager(Core plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Core-Wipes");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        stop();
        long currentSession = ++session;
        if (!plugin.configs().config().getBoolean("VAIP.ENABLED", false)) {
            return;
        }
        ConfigurationSection section = plugin.configs().config().getConfigurationSection("VAIP.SERVERS");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection wipe = section.getConfigurationSection(name);
            if (wipe == null) {
                continue;
            }
            long seconds = TimeParse.seconds(wipe.getString("TIME", ""));
            if (seconds <= 0L) {
                plugin.getLogger().warning("Вайп " + name + " пропущен: укажите TIME в формате 7d, 12h или 30m.");
                continue;
            }
            String key = key(name);
            String id = wipe.getString("ID", name);
            if (id == null || id.trim().isEmpty()) {
                plugin.getLogger().warning("Вайп " + name + " пропущен: укажите непустой ID.");
                continue;
            }
            id = id.trim();
            String display = wipe.getString("NAME", name);
            Wipe timer = new Wipe(key, id, display);
            wipes.put(key, timer);
            long expires = System.currentTimeMillis() + seconds * 1000L;
            execute(() -> load(currentSession, timer, expires));
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(currentSession), 20L, 20L);
    }

    public void reload() {
        start();
    }

    public void stop() {
        session++;
        if (task != null) {
            task.cancel();
            task = null;
        }
        wipes.clear();
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

    public String remaining(String name) {
        Wipe wipe = wipes.get(key(name));
        if (wipe == null || wipe.state == null) {
            return "";
        }
        long millis = Math.max(0L, wipe.state.expires() - System.currentTimeMillis());
        long seconds = (millis + 999L) / 1000L;
        return TimeFormat.full(plugin, seconds);
    }

    public void announce(String id) {
        for (Wipe wipe : wipes.values()) {
            if (!wipe.id.equalsIgnoreCase(id)) {
                continue;
            }
            List<String> lines = plugin.configs().config().getStringList("VAIP.SERVERS." + wipe.key + ".MESSAGES.COMPLETED");
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String line : lines) {
                    player.sendMessage(plugin.placeholders().apply(player, line.replace("%server%", wipe.display)));
                }
            }
            for (String line : lines) {
                Bukkit.getConsoleSender().sendMessage(plugin.placeholders().apply(null, line.replace("%server%", wipe.display)));
            }
            return;
        }
    }

    private void load(long currentSession, Wipe wipe, long expires) {
        if (!active(currentSession)) {
            return;
        }
        WipeState state = plugin.storage().initializeWipe(wipe.id, expires);
        if (active(currentSession)) {
            wipe.state = state;
        }
    }

    private void tick(long currentSession) {
        if (!active(currentSession)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Wipe wipe : wipes.values()) {
            WipeState state = wipe.state;
            if (state == null || state.announced() || state.expires() > now || wipe.claiming) {
                continue;
            }
            wipe.claiming = true;
            execute(() -> claim(currentSession, wipe, now));
        }
    }

    private void claim(long currentSession, Wipe wipe, long now) {
        boolean claimed = plugin.storage().claimWipe(wipe.id, now);
        WipeState state = plugin.storage().initializeWipe(wipe.id, now);
        if (!active(currentSession)) {
            return;
        }
        wipe.state = state;
        wipe.claiming = false;
        if (!claimed) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (active(currentSession)) {
                plugin.messenger().broadcast(Messenger.WIPE, wipe.id, "");
            }
        });
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

    private String key(String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    private static final class Wipe {

        private final String key;
        private final String id;
        private final String display;
        private volatile WipeState state;
        private volatile boolean claiming;

        private Wipe(String key, String id, String display) {
            this.key = key;
            this.id = id;
            this.display = display;
        }
    }
}
