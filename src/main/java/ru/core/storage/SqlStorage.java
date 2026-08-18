package ru.core.storage;

import ru.core.Core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public abstract class SqlStorage implements Storage {

    protected final Core plugin;
    private Connection connection;

    protected SqlStorage(Core plugin) {
        this.plugin = plugin;
    }

    protected abstract Connection open() throws SQLException;

    protected abstract String playersTable();

    protected abstract String networkTable();

    protected abstract String wipesTable();

    protected abstract String presenceTable();

    protected abstract String reconnectTable();

    protected abstract String wipeInsert();

    protected abstract String presenceUpsert();

    protected abstract String upsert();

    @Override
    public void connect() throws SQLException {
        connection = open();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(playersTable());
            ensureCommandConsoleColumn();
            statement.executeUpdate(networkTable());
            statement.executeUpdate(wipesTable());
            statement.executeUpdate(presenceTable());
            statement.executeUpdate(reconnectTable());
        }
    }

    private void ensureCommandConsoleColumn() throws SQLException {
        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("ALTER TABLE CORE_PLAYERS ADD COLUMN COMMAND_CONSOLE BOOLEAN NOT NULL DEFAULT 0");
        } catch (SQLException exception) {
            String message = exception.getMessage();
            String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("duplicate column") && !lower.contains("already exists")) {
                throw exception;
            }
        }
    }

    @Override
    public synchronized WipeState initializeWipe(String id, long expires) {
        try (PreparedStatement insert = connection().prepareStatement(wipeInsert())) {
            insert.setString(1, id);
            insert.setLong(2, expires);
            insert.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка создания таймера вайпа " + id + ": " + exception.getMessage());
            return new WipeState(id, expires, false);
        }
        try (PreparedStatement select = connection().prepareStatement(
                "SELECT ID, EXPIRES, ANNOUNCED FROM CORE_WIPES WHERE ID = ?")) {
            select.setString(1, id);
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    return new WipeState(result.getString("ID"), result.getLong("EXPIRES"), result.getBoolean("ANNOUNCED"));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка чтения таймера вайпа " + id + ": " + exception.getMessage());
        }
        return new WipeState(id, expires, false);
    }

    @Override
    public synchronized void updatePresence(java.util.UUID uuid, String name, String server, long updated) {
        updatePresences(java.util.List.of(new PlayerPresence(uuid, name, server, updated)));
    }

    @Override
    public synchronized void updatePresences(java.util.Collection<PlayerPresence> players) {
        if (players.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection().prepareStatement(presenceUpsert())) {
            for (PlayerPresence player : players) {
                statement.setString(1, player.uuid().toString());
                statement.setString(2, player.name());
                statement.setString(3, player.server());
                statement.setLong(4, player.updated());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка обновления присутствия игроков: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void removePresence(java.util.UUID uuid, String server) {
        try (PreparedStatement statement = connection().prepareStatement(
                "DELETE FROM CORE_ONLINE WHERE UUID = ? AND SERVER = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, server);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка удаления присутствия игрока: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void cleanupPresence(long updated) {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM CORE_ONLINE WHERE UPDATED < ?")) {
            statement.setLong(1, updated);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка очистки присутствия игроков: " + exception.getMessage());
        }
    }

    @Override
    public synchronized PlayerPresence findPresence(String name, long updatedAfter) {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT UUID, NAME, SERVER, UPDATED FROM CORE_ONLINE "
                        + "WHERE LOWER(NAME) = LOWER(?) AND UPDATED >= ? ORDER BY UPDATED DESC LIMIT 1")) {
            statement.setString(1, name);
            statement.setLong(2, updatedAfter);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new PlayerPresence(java.util.UUID.fromString(result.getString("UUID")),
                            result.getString("NAME"), result.getString("SERVER"), result.getLong("UPDATED"));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Ошибка поиска игрока " + name + ": " + exception.getMessage());
        }
        return null;
    }

    @Override
    public synchronized List<PlayerPresence> presences(long updatedAfter) {
        List<PlayerPresence> players = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT UUID, NAME, SERVER, UPDATED FROM CORE_ONLINE WHERE UPDATED >= ?")) {
            statement.setLong(1, updatedAfter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    players.add(new PlayerPresence(UUID.fromString(result.getString("UUID")), result.getString("NAME"),
                            result.getString("SERVER"), result.getLong("UPDATED")));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Ошибка чтения сетевого онлайна: " + exception.getMessage());
        }
        return players;
    }

    @Override
    public synchronized boolean claimWipe(String id, long now) {
        try (PreparedStatement statement = connection().prepareStatement(
                "UPDATE CORE_WIPES SET ANNOUNCED = 1 WHERE ID = ? AND ANNOUNCED = 0 AND EXPIRES <= ?")) {
            statement.setString(1, id);
            statement.setLong(2, now);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка объявления вайпа " + id + ": " + exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void saveReconnect(ReconnectState state) {
        try (PreparedStatement delete = connection().prepareStatement("DELETE FROM CORE_RECONNECTS WHERE UUID = ?");
             PreparedStatement insert = connection().prepareStatement(
                     "INSERT INTO CORE_RECONNECTS (UUID, NAME, TARGET, EXPIRES) VALUES (?, ?, ?, ?)")) {
            delete.setString(1, state.uuid().toString());
            delete.executeUpdate();
            insert.setString(1, state.uuid().toString());
            insert.setString(2, state.name());
            insert.setString(3, state.target());
            insert.setLong(4, state.expires());
            insert.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка сохранения reconnect: " + exception.getMessage());
        }
    }

    @Override
    public synchronized List<ReconnectState> reconnects(long now) {
        List<ReconnectState> states = new ArrayList<>();
        try (PreparedStatement cleanup = connection().prepareStatement("DELETE FROM CORE_RECONNECTS WHERE EXPIRES < ?");
             PreparedStatement select = connection().prepareStatement(
                     "SELECT UUID, NAME, TARGET, EXPIRES FROM CORE_RECONNECTS WHERE EXPIRES >= ?")) {
            cleanup.setLong(1, now);
            cleanup.executeUpdate();
            select.setLong(1, now);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    states.add(new ReconnectState(UUID.fromString(result.getString("UUID")), result.getString("NAME"),
                            result.getString("TARGET"), result.getLong("EXPIRES")));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Ошибка чтения reconnect: " + exception.getMessage());
        }
        return states;
    }

    @Override
    public synchronized void removeReconnect(UUID uuid, String target) {
        try (PreparedStatement statement = connection().prepareStatement(
                "DELETE FROM CORE_RECONNECTS WHERE UUID = ? AND TARGET = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, target);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка удаления reconnect: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка закрытия соединения: " + exception.getMessage());
        }
        connection = null;
    }

    protected synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            close();
            connection = open();
        }
        return connection;
    }

    @Override
    public synchronized Profile load(UUID uuid, String name, String language) {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT NAME, KILLS, DEATHS, PLAYTIME, LANG, COMMAND_CONSOLE FROM CORE_PLAYERS WHERE UUID = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String stored = result.getString("LANG");
                    Profile profile = new Profile(uuid, name, result.getInt("KILLS"), result.getInt("DEATHS"),
                            result.getLong("PLAYTIME"), stored == null || stored.isEmpty() ? language : stored,
                            result.getBoolean("COMMAND_CONSOLE"));
                    save(profile);
                    return profile;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка загрузки профиля " + name + ": " + exception.getMessage());
            return new Profile(uuid, name, 0, 0, 0L, language, false);
        }
        Profile profile = new Profile(uuid, name, 0, 0, 0L, language, false);
        save(profile);
        return profile;
    }

    @Override
    public synchronized void save(Profile profile) {
        if (profile == null) {
            return;
        }
        saveAll(List.of(profile));
    }

    @Override
    public synchronized void saveAll(Collection<Profile> profiles) {
        if (profiles.isEmpty()) {
            return;
        }
        Connection current = null;
        boolean autoCommit = true;
        try {
            current = connection();
            autoCommit = current.getAutoCommit();
            if (autoCommit) {
                current.setAutoCommit(false);
            }
            try (PreparedStatement statement = current.prepareStatement(upsert())) {
                long savedAt = System.currentTimeMillis();
                for (Profile profile : profiles) {
                    if (profile == null) {
                        continue;
                    }
                    bind(statement, profile, savedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            current.commit();
        } catch (SQLException exception) {
            rollback(current);
            plugin.getLogger().warning("Ошибка пакетного сохранения профилей: " + exception.getMessage());
        } finally {
            restoreAutoCommit(current, autoCommit);
        }
    }

    private void bind(PreparedStatement statement, Profile profile, long savedAt) throws SQLException {
        statement.setString(1, profile.uuid().toString());
        statement.setString(2, profile.name());
        statement.setInt(3, profile.kills());
        statement.setInt(4, profile.deaths());
        statement.setLong(5, profile.playtime());
        statement.setString(6, profile.language());
        statement.setBoolean(7, profile.commandConsole());
        statement.setLong(8, savedAt);
    }

    private void rollback(Connection current) {
        if (current == null) {
            return;
        }
        try {
            current.rollback();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка rollback SQL-транзакции: " + exception.getMessage());
        }
    }

    private void restoreAutoCommit(Connection current, boolean autoCommit) {
        if (current == null || !autoCommit) {
            return;
        }
        try {
            current.setAutoCommit(true);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка восстановления auto-commit: " + exception.getMessage());
        }
    }

    @Override
    public synchronized boolean addTime(String name, long seconds) {
        return update("UPDATE CORE_PLAYERS SET PLAYTIME = PLAYTIME + ? WHERE NAME = ?", seconds, name);
    }

    @Override
    public synchronized boolean setTime(String name, long seconds) {
        return update("UPDATE CORE_PLAYERS SET PLAYTIME = ? WHERE NAME = ?", Math.max(0L, seconds), name);
    }

    private boolean update(String sql, long value, String name) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, value);
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка обновления времени " + name + ": " + exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void publish(String server, String type, String sender, String payload) {
        if (!network()) {
            return;
        }
        try (PreparedStatement statement = connection().prepareStatement(
                "INSERT INTO CORE_NETWORK (SERVER, TYPE, SENDER, PAYLOAD, CREATED) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, server);
            statement.setString(2, type);
            statement.setString(3, sender);
            statement.setString(4, payload);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка отправки сетевого сообщения: " + exception.getMessage());
        }
    }

    @Override
    public synchronized List<NetworkMessage> poll(long lastId, String server, int limit) {
        List<NetworkMessage> messages = new ArrayList<>();
        if (!network()) {
            return messages;
        }
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT ID, SERVER, TYPE, SENDER, PAYLOAD FROM CORE_NETWORK WHERE ID > ? AND SERVER <> ? ORDER BY ID ASC LIMIT ?")) {
            statement.setLong(1, lastId);
            statement.setString(2, server);
            statement.setInt(3, Math.max(1, Math.min(limit, 1000)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new NetworkMessage(result.getLong("ID"), result.getString("SERVER"),
                            result.getString("TYPE"), result.getString("SENDER"), result.getString("PAYLOAD")));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка чтения сетевых сообщений: " + exception.getMessage());
        }
        return messages;
    }

    @Override
    public synchronized long lastId() {
        if (!network()) {
            return 0L;
        }
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(ID) FROM CORE_NETWORK")) {
            if (result.next()) {
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка чтения счётчика сообщений: " + exception.getMessage());
        }
        return 0L;
    }

    @Override
    public synchronized void cleanup(long created) {
        if (!network()) {
            return;
        }
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM CORE_NETWORK WHERE CREATED < ?")) {
            statement.setLong(1, created);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Ошибка очистки сетевых сообщений: " + exception.getMessage());
        }
    }
}
