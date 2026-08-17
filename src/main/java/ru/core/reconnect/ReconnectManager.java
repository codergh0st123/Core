package ru.core.reconnect;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.storage.ReconnectState;
import ru.core.text.Msg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class ReconnectManager {

    private final Core plugin;
    private final ExecutorService executor;
    private final Map<UUID, ReconnectState> states = new HashMap<>();
    private BukkitTask task;
    private long session;
    private int ticks;

    public ReconnectManager(Core plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Core-Reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        stop();
        long current = ++session;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(current), 20L, 20L);
    }

    public void reload() {
        start();
    }

    public void handoff() {
        if (!enabled() || !plugin.storage().network()) {
            return;
        }
        String lobby = plugin.proxyConnector().fallbackServer();
        if (lobby == null) {
            return;
        }
        String source = plugin.messenger().server();
        long expires = System.currentTimeMillis() + timeoutMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.storage().saveReconnect(new ReconnectState(player.getUniqueId(), player.getName(), source, expires));
            plugin.proxyConnector().connect(player, lobby);
        }
    }

    public void stop() {
        session++;
        states.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void refresh(long current) {
        if (!active(current)) {
            return;
        }
        executor.execute(() -> {
            Map<UUID, ReconnectState> loaded = new HashMap<>();
            for (ReconnectState state : plugin.storage().reconnects(System.currentTimeMillis())) {
                loaded.put(state.uuid(), state);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active(current)) {
                    states.clear();
                    states.putAll(loaded);
                    update();
                }
            });
        });
    }

    private void update() {
        String server = plugin.messenger().server();
        String lobby = plugin.proxyConnector().fallbackServer();
        boolean retry = ++ticks % retryTicks() == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ReconnectState state = states.get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            if (state.target().equalsIgnoreCase(server)) {
                plugin.storage().removeReconnect(player.getUniqueId(), server);
                continue;
            }
            if (lobby == null || !server.equalsIgnoreCase(lobby)) {
                continue;
            }
            waiting(player, state);
            if (retry) {
                plugin.proxyConnector().connect(player, state.target());
            }
        }
    }

    private void waiting(Player player, ReconnectState state) {
        int frame = (int) ((System.currentTimeMillis() / 500L) % 3L) + 1;
        String dots = ".".repeat(frame);
        String line = Msg.format(plugin, player, plugin.configs().config().getString(
                "RECONNECT.ACTIONBAR", "&eRECONNECT%dots% &8» &fПодождите немного..."),
                "%dots%", dots, "%server%", state.target());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(line));
    }

    private boolean enabled() {
        return plugin.configs().config().getBoolean("RECONNECT.ENABLED", true);
    }

    private long timeoutMillis() {
        return Math.max(30L, plugin.configs().config().getLong("RECONNECT.TIMEOUT-SECONDS", 300L)) * 1000L;
    }

    private int retryTicks() {
        return Math.max(1, plugin.configs().config().getInt("RECONNECT.RETRY-SECONDS", 3));
    }

    private boolean active(long current) {
        return plugin.isEnabled() && session == current;
    }
}
