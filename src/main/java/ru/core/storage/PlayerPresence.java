package ru.core.storage;

import java.util.UUID;

public final class PlayerPresence {

    private final UUID uuid;
    private final String name;
    private final String server;
    private final long updated;

    public PlayerPresence(UUID uuid, String name, String server, long updated) {
        this.uuid = uuid;
        this.name = name;
        this.server = server;
        this.updated = updated;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String server() {
        return server;
    }

    public long updated() {
        return updated;
    }
}
