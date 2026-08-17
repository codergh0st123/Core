package ru.core.storage;

import ru.core.Core;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class SqLiteStorage extends SqlStorage {

    private final File file;

    public SqLiteStorage(Core plugin) {
        super(plugin);
        this.file = new File(plugin.getDataFolder(), plugin.configs().config().getString("DATABASE.SQLITE.FILE", "database.db"));
    }

    @Override
    protected Connection open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Драйвер SQLite не найден", exception);
        }
        File folder = file.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new SQLException("Не удалось создать папку " + folder.getAbsolutePath());
        }
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    @Override
    public boolean network() {
        return false;
    }

    @Override
    protected String playersTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_PLAYERS ("
                + "UUID VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "NAME VARCHAR(16) NOT NULL, "
                + "KILLS INTEGER NOT NULL DEFAULT 0, "
                + "DEATHS INTEGER NOT NULL DEFAULT 0, "
                + "PLAYTIME INTEGER NOT NULL DEFAULT 0, "
                + "LANG VARCHAR(8) NOT NULL DEFAULT 'RU', "
                + "LAST_SEEN INTEGER NOT NULL DEFAULT 0)";
    }

    @Override
    protected String networkTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_NETWORK ("
                + "ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "SERVER VARCHAR(64) NOT NULL, "
                + "TYPE VARCHAR(32) NOT NULL, "
                + "SENDER VARCHAR(32) NOT NULL, "
                + "PAYLOAD TEXT NOT NULL, "
                + "CREATED INTEGER NOT NULL)";
    }

    @Override
    protected String wipesTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_WIPES ("
                + "ID VARCHAR(96) NOT NULL PRIMARY KEY, "
                + "EXPIRES INTEGER NOT NULL, "
                + "ANNOUNCED INTEGER NOT NULL DEFAULT 0)";
    }

    @Override
    protected String presenceTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_ONLINE ("
                + "UUID VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "NAME VARCHAR(16) NOT NULL, "
                + "SERVER VARCHAR(64) NOT NULL, "
                + "UPDATED INTEGER NOT NULL)";
    }

    @Override
    protected String presenceUpsert() {
        return "INSERT INTO CORE_ONLINE (UUID, NAME, SERVER, UPDATED) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET NAME = excluded.NAME, SERVER = excluded.SERVER, UPDATED = excluded.UPDATED";
    }

    @Override
    protected String wipeInsert() {
        return "INSERT OR IGNORE INTO CORE_WIPES (ID, EXPIRES, ANNOUNCED) VALUES (?, ?, 0)";
    }

    @Override
    protected String upsert() {
        return "INSERT INTO CORE_PLAYERS (UUID, NAME, KILLS, DEATHS, PLAYTIME, LANG, LAST_SEEN) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET NAME = excluded.NAME, KILLS = excluded.KILLS, DEATHS = excluded.DEATHS, "
                + "PLAYTIME = excluded.PLAYTIME, LANG = excluded.LANG, LAST_SEEN = excluded.LAST_SEEN";
    }
}
