package ru.core.storage;

public final class WipeState {

    private final String id;
    private final long expires;
    private final boolean announced;

    public WipeState(String id, long expires, boolean announced) {
        this.id = id;
        this.expires = expires;
        this.announced = announced;
    }

    public String id() {
        return id;
    }

    public long expires() {
        return expires;
    }

    public boolean announced() {
        return announced;
    }
}
