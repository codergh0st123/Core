package ru.core.storage;

import ru.core.Core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class MySqlStorage extends SqlStorage {

    private final String url;
    private final Properties properties = new Properties();

    public MySqlStorage(Core plugin) {
        super(plugin);
        String host = plugin.configs().config().getString("DATABASE.MYSQL.HOST", "127.0.0.1");
        int port = plugin.configs().config().getInt("DATABASE.MYSQL.PORT", 3306);
        String database = plugin.configs().config().getString("DATABASE.MYSQL.DATABASE", "core");
        boolean ssl = plugin.configs().config().getBoolean("DATABASE.MYSQL.SSL", false);
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl + "&characterEncoding=UTF-8&autoReconnect=true";
        properties.setProperty("user", plugin.configs().config().getString("DATABASE.MYSQL.USER", "root"));
        properties.setProperty("password", plugin.configs().config().getString("DATABASE.MYSQL.PASSWORD", ""));
    }

    @Override
    protected Connection open() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Драйвер MySQL не найден", exception);
        }
        return DriverManager.getConnection(url, properties);
    }

    @Override
    public boolean network() {
        return true;
    }

    @Override
    protected String playersTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_PLAYERS ("
                + "UUID VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "NAME VARCHAR(16) NOT NULL, "
                + "KILLS INT NOT NULL DEFAULT 0, "
                + "DEATHS INT NOT NULL DEFAULT 0, "
                + "PLAYTIME BIGINT NOT NULL DEFAULT 0, "
                + "LANG VARCHAR(8) NOT NULL DEFAULT 'RU', "
                + "LAST_SEEN BIGINT NOT NULL DEFAULT 0, "
                + "INDEX CORE_PLAYERS_NAME (NAME)) DEFAULT CHARSET utf8mb4";
    }

    @Override
    protected String networkTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_NETWORK ("
                + "ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "SERVER VARCHAR(64) NOT NULL, "
                + "TYPE VARCHAR(32) NOT NULL, "
                + "SENDER VARCHAR(32) NOT NULL, "
                + "PAYLOAD TEXT NOT NULL, "
                + "CREATED BIGINT NOT NULL) DEFAULT CHARSET utf8mb4";
    }

    @Override
    protected String wipesTable() {
        return "CREATE TABLE IF NOT EXISTS CORE_WIPES ("
                + "ID VARCHAR(96) NOT NULL PRIMARY KEY, "
                + "EXPIRES BIGINT NOT NULL, "
                + "ANNOUNCED TINYINT(1) NOT NULL DEFAULT 0) DEFAULT CHARSET utf8mb4";
    }

    @Override
    protected String wipeInsert() {
        return "INSERT INTO CORE_WIPES (ID, EXPIRES, ANNOUNCED) VALUES (?, ?, 0) "
                + "ON DUPLICATE KEY UPDATE ID = VALUES(ID)";
    }

    @Override
    protected String upsert() {
        return "INSERT INTO CORE_PLAYERS (UUID, NAME, KILLS, DEATHS, PLAYTIME, LANG, LAST_SEEN) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), KILLS = VALUES(KILLS), DEATHS = VALUES(DEATHS), "
                + "PLAYTIME = VALUES(PLAYTIME), LANG = VALUES(LANG), LAST_SEEN = VALUES(LAST_SEEN)";
    }
}
