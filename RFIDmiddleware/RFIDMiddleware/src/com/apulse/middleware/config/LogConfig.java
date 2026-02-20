package com.apulse.middleware.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class LogConfig {
    private static final String CONFIG_FILE = "config" + File.separator + "log.cfg";

    private String level = "INFO";
    private boolean fileEnabled = true;
    private String filePath = "logs/middleware.log";
    private int maxSizeMB = 10;
    private int maxCount = 5;
    private boolean consoleEnabled = true;

    public LogConfig() {
        load();
    }

    private void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            System.out.println("[LogConfig] Config file not found: " + CONFIG_FILE + " (using defaults)");
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            level = props.getProperty("log.level", level).trim().toUpperCase();
            fileEnabled = Boolean.parseBoolean(props.getProperty("log.file.enabled", String.valueOf(fileEnabled)));
            filePath = props.getProperty("log.file.path", filePath).trim();
            maxSizeMB = Integer.parseInt(props.getProperty("log.file.max.size", String.valueOf(maxSizeMB)));
            maxCount = Integer.parseInt(props.getProperty("log.file.max.count", String.valueOf(maxCount)));
            consoleEnabled = Boolean.parseBoolean(props.getProperty("log.console.enabled", String.valueOf(consoleEnabled)));
            System.out.println("[LogConfig] Loaded from " + CONFIG_FILE);
        } catch (Exception e) {
            System.out.println("[LogConfig] Error loading config: " + e.getMessage() + " (using defaults)");
        }
    }

    public String getLevel() { return level; }
    public boolean isFileEnabled() { return fileEnabled; }
    public String getFilePath() { return filePath; }
    public int getMaxSizeMB() { return maxSizeMB; }
    public int getMaxCount() { return maxCount; }
    public boolean isConsoleEnabled() { return consoleEnabled; }
}
