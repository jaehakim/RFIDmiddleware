package com.apulse.middleware.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "config" + File.separator + "database.cfg";

    private String host = "localhost";
    private int port = 3306;
    private String dbName = "rfid_middleware";
    private String user = "rfid";
    private String password = "rfid1234";
    private int cacheTtlSeconds = 30;
    private int cacheMaxSize = 10000;

    public DatabaseConfig() {
        load();
    }

    private void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            System.out.println("[DatabaseConfig] Config file not found: " + CONFIG_FILE + " (using defaults)");
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            host = props.getProperty("db.host", host);
            port = Integer.parseInt(props.getProperty("db.port", String.valueOf(port)));
            dbName = props.getProperty("db.name", dbName);
            user = props.getProperty("db.user", user);
            password = props.getProperty("db.password", password);
            cacheTtlSeconds = Integer.parseInt(props.getProperty("cache.ttl.seconds", String.valueOf(cacheTtlSeconds)));
            cacheMaxSize = Integer.parseInt(props.getProperty("cache.max.size", String.valueOf(cacheMaxSize)));
            System.out.println("[DatabaseConfig] Loaded from " + CONFIG_FILE);
        } catch (Exception e) {
            System.out.println("[DatabaseConfig] Error loading config: " + e.getMessage() + " (using defaults)");
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDbName() { return dbName; }
    public String getUser() { return user; }
    public String getPassword() { return password; }
    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public int getCacheMaxSize() { return cacheMaxSize; }

    public String getJdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + dbName
            + "?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true";
    }
}
