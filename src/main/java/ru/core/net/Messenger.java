package ru.core.net;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.storage.NetworkMessage;
import ru.core.text.Colors;
import ru.core.text.Msg;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Messenger {

    public static final String ALERT = "ALERT";
    public static final String PREMIUM = "PREMIUM";
    public static final String STAFF = "STAFF";
    public static final String WIPE = "WIPE";

    private final Core plugin;
    private final ExecutorService networkExecutor;
    private final AtomicBoolean pollPending = new AtomicBoolean();
    private volatile long session;
    private String server = "server-1";
    private long lastId;
    private int polls;
    private BukkitTask task;

    public Messenger(Core plugin) {
        this.plugin = plugin;
        this.networkExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Core-Network");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        stop();
        long currentSession = ++session;
        polls = 0;
        lastId = 0L;
        server = plugin.configs().config().getString("SERVER", "server-1");
        if (!plugin.storage().network()) {
            return;
        }
        execute(() -> initialize(currentSession));
        long period = Math.max(20L, plugin.configs().config().getLong("DATABASE.SYNC-INTERVAL", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> schedulePoll(currentSession), period, period);
    }

    public void stop() {
        session++;
        pollPending.set(false);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        networkExecutor.shutdown();
        try {
            if (!networkExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                networkExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            networkExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public String server() {
        return server;
    }

    public void broadcast(String type, String sender, String message) {
        deliver(type, server, sender, message);
        if (!plugin.storage().network()) {
            return;
        }
        String target = server;
        execute(() -> plugin.storage().publish(target, type, sender, message));
    }

    private void schedulePoll(long currentSession) {
        if (!active(currentSession) || !pollPending.compareAndSet(false, true)) {
            return;
        }
        execute(() -> {
            try {
                poll(currentSession);
            } finally {
                pollPending.set(false);
            }
        });
    }

    private void initialize(long currentSession) {
        if (!active(currentSession)) {
            return;
        }
        long id = plugin.storage().lastId();
        if (active(currentSession)) {
            lastId = id;
        }
    }

    private void poll(long currentSession) {
        if (!active(currentSession)) {
            return;
        }
        List<NetworkMessage> messages = plugin.storage().poll(lastId, server, messagesPerPoll());
        if (!active(currentSession)) {
            return;
        }
        if (++polls >= cleanupEveryPolls()) {
            polls = 0;
            plugin.storage().cleanup(System.currentTimeMillis() - retentionMillis());
        }
        if (messages.isEmpty()) {
            return;
        }
        for (NetworkMessage message : messages) {
            if (message.id() > lastId) {
                lastId = message.id();
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!active(currentSession)) {
                return;
            }
            for (NetworkMessage message : messages) {
                deliver(message.type(), message.server(), message.sender(), message.payload());
            }
        });
    }

    private boolean active(long currentSession) {
        return plugin.isEnabled() && session == currentSession;
    }

    private void execute(Runnable task) {
        if (networkExecutor.isShutdown()) {
            return;
        }
        try {
            networkExecutor.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private int messagesPerPoll() {
        return Math.max(1, plugin.configs().config().getInt("DATABASE.SYNC.MESSAGES-PER-POLL", 250));
    }

    private int cleanupEveryPolls() {
        return Math.max(1, plugin.configs().config().getInt("DATABASE.SYNC.CLEANUP-EVERY-POLLS", 300));
    }

    private long retentionMillis() {
        long seconds = Math.max(60L, plugin.configs().config().getLong("DATABASE.SYNC.RETENTION-SECONDS", 300L));
        return seconds * 1000L;
    }

    private void deliver(String type, String source, String sender, String message) {
        if (ALERT.equals(type)) {
            send(null, "ALERT", source, sender, message, false);
        } else if (PREMIUM.equals(type)) {
            send("core.chat.premium", "PREMIUM-CHAT", source, sender, message, true);
        } else if (STAFF.equals(type)) {
            send("core.chat.staff", "STAFF-CHAT", source, sender, message, false);
        } else if (WIPE.equals(type)) {
            plugin.wipeManager().announce(sender);
        }
    }

    private void send(String permission, String path, String source, String sender, String message, boolean hover) {
        List<String> lines = plugin.configs().messages(path);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission != null && !player.hasPermission(permission)) {
                continue;
            }
            for (String line : lines) {
                String formatted = insert(Msg.format(plugin, player, line), source, sender, message);
                if (hover) {
                    sendPremium(player, formatted, source);
                } else {
                    player.sendMessage(formatted);
                }
            }
        }
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(insert(Colors.apply(line), source, sender, message));
        }
    }

    private void sendPremium(Player player, String message, String source) {
        BaseComponent[] components = TextComponent.fromLegacyText(message);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(hover(player, source)));
        for (BaseComponent component : components) {
            component.setHoverEvent(hover);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/play " + source));
        }
        player.spigot().sendMessage(components);
    }

    private String hover(Player player, String source) {
        List<String> lines = plugin.configs().messages("PREMIUM-CHAT-HOVER");
        if (lines.isEmpty()) {
            return Colors.apply("&7Сервер: &f" + source);
        }
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(Msg.format(plugin, player, line).replace("%server%", Colors.apply(source)));
        }
        return text.toString();
    }

    private String insert(String line, String source, String sender, String message) {
        return line.replace("%player%", sender)
                .replace("%message%", Colors.apply(message))
                .replace("%server%", Colors.apply(source));
    }
}
