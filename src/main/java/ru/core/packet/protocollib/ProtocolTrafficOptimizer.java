package ru.core.packet.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import org.bukkit.entity.Player;
import ru.core.Core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cancels only immediate duplicate scoreboard packets emitted by Core.
 * Packets that do not describe a CORE_ scoreboard resource are never changed.
 */
public final class ProtocolTrafficOptimizer {

    private static final PacketType[] PACKET_TYPES = {
            PacketType.Play.Server.SCOREBOARD_OBJECTIVE,
            PacketType.Play.Server.SCOREBOARD_TEAM,
            PacketType.Play.Server.SCOREBOARD_SCORE
    };

    private final Core plugin;
    private final Map<UUID, PacketDeduplicator> states = new ConcurrentHashMap<>();
    private ProtocolManager manager;
    private PacketListener listener;
    private long duplicateWindowNanos;

    public ProtocolTrafficOptimizer(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.configs().config().getBoolean("PROTOCOLLIB.ENABLED", true)) {
            return;
        }
        long windowMillis = Math.max(0L,
                plugin.configs().config().getLong("PROTOCOLLIB.DUPLICATE-WINDOW-MILLIS", 100L));
        if (windowMillis == 0L) {
            return;
        }
        duplicateWindowNanos = windowMillis * 1_000_000L;
        manager = ProtocolLibrary.getProtocolManager();
        listener = new PacketAdapter(plugin, ListenerPriority.NORMAL, PACKET_TYPES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                filter(event);
            }
        };
        manager.addPacketListener(listener);
        plugin.getLogger().info("ProtocolLib: оптимизация дублирующихся scoreboard-пакетов включена.");
    }

    public void stop() {
        if (manager != null && listener != null) {
            manager.removePacketListener(listener);
        }
        listener = null;
        manager = null;
        states.clear();
    }

    public void remove(Player player) {
        if (player != null) {
            states.remove(player.getUniqueId());
        }
    }

    private void filter(PacketEvent event) {
        if (event.isPlayerTemporary() || event.getPlayer() == null) {
            return;
        }
        String payload = event.getPacket().toString();
        if (!payload.contains("CORE_")) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        PacketDeduplicator state = states.computeIfAbsent(uuid, ignored -> new PacketDeduplicator());
        if (state.duplicate(event.getPacket().getType().toString(), payload, System.nanoTime(), duplicateWindowNanos)) {
            event.setCancelled(true);
        }
    }
}
