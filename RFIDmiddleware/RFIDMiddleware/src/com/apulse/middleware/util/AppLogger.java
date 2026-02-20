package com.apulse.middleware.util;

import com.apulse.middleware.config.LogConfig;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class AppLogger {
    private static final Logger logger = Logger.getLogger("RFIDMiddleware");
    private static boolean initialized = false;
    private static Level currentLevel = Level.INFO;
    private static boolean consoleEnabled = true;

    private AppLogger() {}

    public static synchronized void init(LogConfig config) {
        if (initialized) return;

        // 루트 로거 기본 핸들러 제거 (콘솔 중복 방지)
        Logger rootLogger = Logger.getLogger("");
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }

        // 로그 레벨 설정
        currentLevel = toJulLevel(config.getLevel());
        logger.setLevel(currentLevel);
        logger.setUseParentHandlers(false);

        consoleEnabled = config.isConsoleEnabled();

        // 콘솔 핸들러
        if (config.isConsoleEnabled()) {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(currentLevel);
            consoleHandler.setFormatter(new AppFormatter());
            logger.addHandler(consoleHandler);
        }

        // 파일 핸들러
        if (config.isFileEnabled()) {
            try {
                String filePath = config.getFilePath().replace('/', File.separatorChar);
                File logFile = new File(filePath);
                File logDir = logFile.getParentFile();
                if (logDir != null && !logDir.exists()) {
                    logDir.mkdirs();
                }

                int maxBytes = config.getMaxSizeMB() * 1024 * 1024;
                FileHandler fileHandler = new FileHandler(
                    filePath, maxBytes, config.getMaxCount(), true);
                fileHandler.setLevel(currentLevel);
                fileHandler.setFormatter(new AppFormatter());
                fileHandler.setEncoding("UTF-8");
                logger.addHandler(fileHandler);

                System.out.println("[AppLogger] File logging to: " + filePath);
            } catch (IOException e) {
                System.err.println("[AppLogger] Failed to create file handler: " + e.getMessage());
            }
        }

        initialized = true;
        info("AppLogger", "Initialized (level=" + config.getLevel() + ")");
    }

    public static void debug(String component, String message) {
        log(Level.FINE, component, message);
    }

    public static void info(String component, String message) {
        log(Level.INFO, component, message);
    }

    public static void warn(String component, String message) {
        log(Level.WARNING, component, message);
    }

    public static void error(String component, String message) {
        log(Level.SEVERE, component, message);
    }

    public static void error(String component, String message, Throwable t) {
        if (initialized) {
            LogRecord record = new LogRecord(Level.SEVERE, formatMessage(component, message));
            record.setThrown(t);
            logger.log(record);
        } else {
            System.out.println(formatFallback(Level.SEVERE, component, message));
            t.printStackTrace();
        }
    }

    private static void log(Level level, String component, String message) {
        if (initialized) {
            logger.log(level, formatMessage(component, message));
        } else {
            // 초기화 전에는 System.out으로 폴백
            System.out.println(formatFallback(level, component, message));
        }
    }

    private static String formatMessage(String component, String message) {
        return "[" + component + "] " + message;
    }

    private static String formatFallback(Level level, String component, String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "[" + sdf.format(new Date()) + "] [" + toLevelName(level) + "] [" + component + "] " + message;
    }

    private static Level toJulLevel(String level) {
        switch (level) {
            case "DEBUG": return Level.FINE;
            case "WARN":  return Level.WARNING;
            case "ERROR": return Level.SEVERE;
            case "INFO":
            default:      return Level.INFO;
        }
    }

    private static String toLevelName(Level level) {
        if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) return "DEBUG";
        if (level == Level.WARNING) return "WARN";
        if (level == Level.SEVERE) return "ERROR";
        return "INFO";
    }

    /** 커스텀 포매터: [yyyy-MM-dd HH:mm:ss] [LEVEL] message */
    private static class AppFormatter extends Formatter {
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(sdf.format(new Date(record.getMillis()))).append("] ");
            sb.append('[').append(toLevelName(record.getLevel())).append("] ");
            sb.append(record.getMessage());
            sb.append(System.lineSeparator());

            if (record.getThrown() != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw.toString());
            }

            return sb.toString();
        }
    }

    public static void shutdown() {
        for (Handler h : logger.getHandlers()) {
            h.close();
        }
    }
}
