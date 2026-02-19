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
            System.out.println("[DatabaseManager] Connected to " + config.getHost() + ":" + config.getPort());
        } catch (Exception e) {
            connection = null;
            System.out.println("[DatabaseManager] Connection failed: " + e.getMessage());
        }
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS tag_reads ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
            + "epc VARCHAR(128) NOT NULL, "
            + "reader_name VARCHAR(64) NOT NULL, "
            + "rssi INT NOT NULL, "
            + "read_time DATETIME NOT NULL, "
            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
            + "INDEX idx_epc (epc), "
            + "INDEX idx_read_time (read_time), "
            + "INDEX idx_reader (reader_name)"
            + ") ENGINE=InnoDB";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DatabaseManager] Table 'tag_reads' ready");
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
