package ru.core.packet.protocollib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the most recent payload for every packet type using a monotonic clock.
 */
final class PacketDeduplicator {

    private final Map<String, PacketStamp> lastPackets = new ConcurrentHashMap<>();

    boolean duplicate(String type, String payload, long sentAtNanos, long windowNanos) {
        PacketStamp current = new PacketStamp(payload, sentAtNanos);
        PacketStamp previous = lastPackets.put(type, current);
        if (previous == null || !previous.payload.equals(payload)) {
            return false;
        }
        long elapsed = sentAtNanos - previous.sentAtNanos;
        return elapsed >= 0L && elapsed <= windowNanos;
    }

    void clear() {
        lastPackets.clear();
    }

    private static final class PacketStamp {

        private final String payload;
        private final long sentAtNanos;

        private PacketStamp(String payload, long sentAtNanos) {
            this.payload = payload;
            this.sentAtNanos = sentAtNanos;
        }
    }
}
