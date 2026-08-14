package ru.core.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface Storage {

    void connect() throws Exception;

    void close();

    boolean network();

    Profile load(UUID uuid, String name, String language);

    void save(Profile profile);

    void saveAll(Collection<Profile> profiles);

    boolean addTime(String name, long seconds);

    boolean setTime(String name, long seconds);

    void publish(String server, String type, String sender, String payload);

    List<NetworkMessage> poll(long lastId, String server, int limit);

    long lastId();

    void cleanup(long created);
}
