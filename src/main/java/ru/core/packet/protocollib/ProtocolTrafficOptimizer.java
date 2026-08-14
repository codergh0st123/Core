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
    private final Map<UUID, PlayerPacketState> states = new ConcurrentHashMap<>();
    private ProtocolManager manager;
    private PacketListener listener;
    private long duplicateWindowMillis;

    public ProtocolTrafficOptimizer(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.configs().config().getBoolean("PROTOCOLLIB.ENABLED", true)) {
            return;
        }
        duplicateWindowMillis = Math.max(0L,
                plugin.configs().config().getLong("PROTOCOLLIB.DUPLICATE-WINDOW-MILLIS", 100L));
        if (duplicateWindowMillis == 0L) {
            return;
        }
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
        if (event.isPlayerTemporary() || event.getPlayer() == null || !owns(event)) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        PacketFingerprint fingerprint = new PacketFingerprint(event.getPacket().getType(), event.getPacket().toString(),
                System.currentTimeMillis());
        PlayerPacketState state = states.computeIfAbsent(uuid, ignored -> new PlayerPacketState());
        if (state.duplicate(fingerprint, duplicateWindowMillis)) {
            event.setCancelled(true);
        }
    }

    private boolean owns(PacketEvent event) {
        return event.getPacket().toString().contains("CORE_");
    }

    private static final class PlayerPacketState {

        private final Map<PacketType, PacketFingerprint> lastPackets = new ConcurrentHashMap<>();

        private boolean duplicate(PacketFingerprint fingerprint, long windowMillis) {
            PacketFingerprint previous = lastPackets.put(fingerprint.type, fingerprint);
            return previous != null
                    && previous.payload.equals(fingerprint.payload)
                    && fingerprint.sentAt - previous.sentAt <= windowMillis;
        }
    }

    private static final class PacketFingerprint {

        private final PacketType type;
        private final String payload;
        private final long sentAt;

        private PacketFingerprint(PacketType type, String payload, long sentAt) {
            this.type = type;
            this.payload = payload;
            this.sentAt = sentAt;
        }
    }
}
