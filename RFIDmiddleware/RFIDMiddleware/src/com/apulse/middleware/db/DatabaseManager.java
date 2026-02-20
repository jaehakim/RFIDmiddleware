package com.apulse.middleware.db;

import com.apulse.middleware.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseManager {
    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private DatabaseConfig config;
    private Connection connection;
    private boolean initialized = false;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public void initialize(DatabaseConfig config) {
        this.config = config;
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            connect();
            if (connection != null) {
                createTableIfNotExists();
                initialized = true;
                System.out.println("[DatabaseManager] Initialized successfully");
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Init failed: " + e.getMessage());
            System.out.println("[DatabaseManager] Program will continue without DB");
        }
    }

    private void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            connection = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUser(), config.getPassword());
            try (Statement s = connection.createStatement()) {
                s.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            System.out.println("[DatabaseManager] Connected to " + config.getHost() + ":" + config.getPort());
        } catch (Exception e) {
            connection = null;
            System.out.println("[DatabaseManager] Connection failed: " + e.getMessage());
        }
    }

    private void createTableIfNotExists() {
        String[] sqls = {
            "CREATE TABLE IF NOT EXISTS tag_reads ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "epc VARCHAR(128) NOT NULL, "
                + "reader_name VARCHAR(64) NOT NULL, "
                + "rssi INT NOT NULL, "
                + "read_time DATETIME NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_epc (epc), "
                + "INDEX idx_read_time (read_time), "
                + "INDEX idx_reader (reader_name)"
                + ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS assets ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "asset_number VARCHAR(64) NOT NULL, "
                + "epc VARCHAR(128) NOT NULL, "
                + "asset_name VARCHAR(128), "
                + "department VARCHAR(64), "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uk_epc (epc), "
                + "INDEX idx_asset_number (asset_number)"
                + ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS export_permissions ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "epc VARCHAR(128) NOT NULL, "
                + "permit_start DATETIME, "
                + "permit_end DATETIME, "
                + "reason VARCHAR(256), "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_epc (epc), "
                + "INDEX idx_permit_period (permit_start, permit_end)"
                + ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS export_alerts ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "epc VARCHAR(128) NOT NULL, "
                + "asset_number VARCHAR(64), "
                + "reader_name VARCHAR(64) NOT NULL, "
                + "rssi INT, "
                + "alert_time DATETIME NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_alert_time (alert_time), "
                + "INDEX idx_epc (epc)"
                + ") ENGINE=InnoDB"
        };

        String[] tableNames = {"tag_reads", "assets", "export_permissions", "export_alerts"};
        try (Statement stmt = connection.createStatement()) {
            for (int i = 0; i < sqls.length; i++) {
                stmt.execute(sqls[i]);
                System.out.println("[DatabaseManager] Table '" + tableNames[i] + "' ready");
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Create table failed: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        if (!initialized) return null;
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                System.out.println("[DatabaseManager] Reconnecting...");
                connect();
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Reconnect check failed: " + e.getMessage());
            connect();
        }
        return connection;
    }

    public boolean isAvailable() {
        return initialized && connection != null;
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DatabaseManager] Connection closed");
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Shutdown error: " + e.getMessage());
        }
        initialized = false;
    }
}
