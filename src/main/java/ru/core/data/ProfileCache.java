package ru.core.data;

import ru.core.storage.Profile;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded in-memory cache for recently disconnected player profiles.
 * All methods must be invoked from the Bukkit main thread.
 */
public final class ProfileCache {

    private final Map<UUID, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
    private boolean enabled;
    private int maximumEntries;
    private long ttlMillis;

    public void configure(boolean enabled, int maximumEntries, long ttlMillis) {
        this.enabled = enabled;
        this.maximumEntries = Math.max(1, maximumEntries);
        this.ttlMillis = Math.max(1000L, ttlMillis);
        if (!enabled) {
            clear();
            return;
        }
        cleanup(System.currentTimeMillis());
        trim();
    }

    public Profile take(UUID uuid, long now) {
        if (!enabled) {
            return null;
        }
        Entry entry = entries.remove(uuid);
        if (entry == null || expired(entry, now)) {
            return null;
        }
        return entry.profile;
    }

    public void put(Profile profile, long now) {
        if (!enabled || profile == null) {
            return;
        }
        entries.put(profile.uuid(), new Entry(profile, now));
        trim();
    }

    public void cleanup(long now) {
        if (!enabled || entries.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (expired(iterator.next().getValue(), now)) {
                iterator.remove();
            }
        }
        trim();
    }

    public void remove(String name) {
        if (name == null || entries.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.profile.name().equalsIgnoreCase(name)) {
                iterator.remove();
                return;
            }
        }
    }

    public void clear() {
        entries.clear();
    }

    private void trim() {
        Iterator<UUID> iterator = entries.keySet().iterator();
        while (entries.size() > maximumEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private boolean expired(Entry entry, long now) {
        return now - entry.storedAt >= ttlMillis;
    }

    private static final class Entry {

        private final Profile profile;
        private final long storedAt;

        private Entry(Profile profile, long storedAt) {
            this.profile = profile;
            this.storedAt = storedAt;
        }
    }
}
