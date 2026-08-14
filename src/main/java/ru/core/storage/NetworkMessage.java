package ru.core.storage;

public final class NetworkMessage {

    private final long id;
    private final String server;
    private final String type;
    private final String sender;
    private final String payload;

    public NetworkMessage(long id, String server, String type, String sender, String payload) {
        this.id = id;
        this.server = server;
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    public long id() {
        return id;
    }

    public String server() {
        return server;
    }

    public String type() {
        return type;
    }

    public String sender() {
        return sender;
    }

    public String payload() {
        return payload;
    }
}
