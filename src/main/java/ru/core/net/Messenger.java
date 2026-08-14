package ru.core.net;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.core.Core;
import ru.core.storage.NetworkMessage;
import ru.core.text.Colors;
import ru.core.text.Msg;

import java.util.List;

public final class Messenger {

    public static final String ALERT = "ALERT";
    public static final String PREMIUM = "PREMIUM";
    public static final String STAFF = "STAFF";

    private final Core plugin;
    private volatile long session;
    private String server = "server-1";
    private long lastId;
    private int polls;
    private BukkitTask task;

    public Messenger(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long currentSession = ++session;
        polls = 0;
        server = plugin.configs().config().getString("SERVER", "server-1");
        if (!plugin.storage().network()) {
            return;
        }
        plugin.data().async(() -> initialize(currentSession));
        long period = Math.max(20L, plugin.configs().config().getLong("DATABASE.SYNC-INTERVAL", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> plugin.data().async(() -> poll(currentSession)), period, period);
    }

    public void stop() {
        session++;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public String server() {
        return server;
    }

    public void broadcast(String type, String sender, String message) {
        deliver(type, sender, message);
        if (!plugin.storage().network()) {
            return;
        }
        String target = server;
        plugin.data().async(() -> plugin.storage().publish(target, type, sender, message));
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
                deliver(message.type(), message.sender(), message.payload());
            }
        });
    }

    private boolean active(long currentSession) {
        return plugin.isEnabled() && session == currentSession;
    }

    private int messagesPerPoll() {
        return Math.max(1, plugin.configs().config().getInt("DATABASE.SYNC.MESSAGES-PER-POLL", 100));
    }

    private int cleanupEveryPolls() {
        return Math.max(1, plugin.configs().config().getInt("DATABASE.SYNC.CLEANUP-EVERY-POLLS", 50));
    }

    private long retentionMillis() {
        long seconds = Math.max(60L, plugin.configs().config().getLong("DATABASE.SYNC.RETENTION-SECONDS", 120L));
        return seconds * 1000L;
    }

    private void deliver(String type, String sender, String message) {
        if (ALERT.equals(type)) {
            send(null, "ALERT", sender, message);
        } else if (PREMIUM.equals(type)) {
            send("core.chat.premium", "PREMIUM-CHAT", sender, message);
        } else if (STAFF.equals(type)) {
            send("core.chat.staff", "STAFF-CHAT", sender, message);
        }
    }

    private void send(String permission, String path, String sender, String message) {
        List<String> lines = plugin.configs().messages(path);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission != null && !player.hasPermission(permission)) {
                continue;
            }
            for (String line : lines) {
                player.sendMessage(insert(Msg.format(plugin, player, line), sender, message));
            }
        }
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(insert(Colors.apply(line), sender, message));
        }
    }

    private String insert(String line, String sender, String message) {
        return line.replace("%player%", sender).replace("%message%", Colors.apply(message));
    }
}
