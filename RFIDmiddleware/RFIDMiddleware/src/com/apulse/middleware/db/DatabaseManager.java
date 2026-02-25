package com.apulse.middleware.db;

import com.apulse.middleware.config.DatabaseConfig;
import com.apulse.middleware.util.AppLogger;

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
                AppLogger.info("DatabaseManager", "Initialized successfully");
            }
        } catch (Exception e) {
            AppLogger.error("DatabaseManager", "Init failed: " + e.getMessage());
            AppLogger.warn("DatabaseManager", "Program will continue without DB");
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
            AppLogger.info("DatabaseManager", "Connected to " + config.getHost() + ":" + config.getPort());
        } catch (Exception e) {
            connection = null;
            AppLogger.error("DatabaseManager", "Connection failed: " + e.getMessage());
        }
    }

    private void createTableIfNotExists() {
        String[] sqls = {
            "CREATE TABLE IF NOT EXISTS tag_reads ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                + "epc VARCHAR(128) NOT NULL, "
                + "reader_name VARCHAR(64) NOT NULL, "
                + "rssi INT NOT NULL, "
                + "antenna INT DEFAULT 0, "
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
                + "possession TINYINT(1) DEFAULT 1, "
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
                AppLogger.info("DatabaseManager", "Table '" + tableNames[i] + "' ready");
            }
            // 기존 assets 테이블에 possession 컬럼이 없으면 추가
            try {
                stmt.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS possession TINYINT(1) DEFAULT 1");
            } catch (Exception ignore) {
                // 이미 존재하거나 지원하지 않는 경우 무시
            }
            // 기존 tag_reads 테이블에 antenna 컬럼이 없으면 추가
            try {
                stmt.execute("ALTER TABLE tag_reads ADD COLUMN IF NOT EXISTS antenna INT DEFAULT 0");
            } catch (Exception ignore) {
                // 이미 존재하거나 지원하지 않는 경우 무시
            }
        } catch (Exception e) {
            AppLogger.error("DatabaseManager", "Create table failed: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        if (!initialized) return null;
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                AppLogger.info("DatabaseManager", "Reconnecting...");
                connect();
            }
        } catch (Exception e) {
            AppLogger.error("DatabaseManager", "Reconnect check failed: " + e.getMessage());
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
                AppLogger.info("DatabaseManager", "Connection closed");
            }
        } catch (Exception e) {
            AppLogger.error("DatabaseManager", "Shutdown error: " + e.getMessage());
        }
        initialized = false;
    }
}
