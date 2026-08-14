package ru.core.storage;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Profile {

    private final UUID uuid;
    private final AtomicInteger kills;
    private final AtomicInteger deaths;
    private final AtomicLong playtime;
    private volatile String name;
    private volatile String language;

    public Profile(UUID uuid, String name, int kills, int deaths, long playtime, String language) {
        this.uuid = uuid;
        this.name = name;
        this.kills = new AtomicInteger(kills);
        this.deaths = new AtomicInteger(deaths);
        this.playtime = new AtomicLong(playtime);
        this.language = language;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int kills() {
        return kills.get();
    }

    public void addKill() {
        kills.incrementAndGet();
    }

    public int deaths() {
        return deaths.get();
    }

    public void addDeath() {
        deaths.incrementAndGet();
    }

    public long playtime() {
        return playtime.get();
    }

    public void addPlaytime(long seconds) {
        playtime.addAndGet(seconds);
    }

    public void playtime(long seconds) {
        playtime.set(Math.max(0L, seconds));
    }

    public String language() {
        return language;
    }

    public void language(String language) {
        this.language = language;
    }
}
