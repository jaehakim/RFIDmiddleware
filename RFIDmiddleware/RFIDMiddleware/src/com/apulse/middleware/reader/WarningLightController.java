package com.apulse.middleware.reader;

import com.apulse.middleware.util.AppLogger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WarningLightController {
    private static final WarningLightController INSTANCE = new WarningLightController();

    private int autoOffDelayMs = 5000;
    private final ConcurrentHashMap<ReaderConnection, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ReaderConnection, ScheduledFuture<?>> buzzerTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    private WarningLightController() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "WarningLight-Timer");
            t.setDaemon(true);
            return t;
        });
    }

    public static WarningLightController getInstance() {
        return INSTANCE;
    }

    public void setAutoOffDelayMs(int delayMs) {
        this.autoOffDelayMs = delayMs;
    }

    /**
     * 경광등 ON + 자동 OFF 스케줄.
     * 같은 리더기에서 재호출 시 타이머를 리셋한다.
     */
    public void triggerWarningLight(ReaderConnection connection) {
        // 기존 타이머가 있으면 취소
        ScheduledFuture<?> existing = activeTimers.get(connection);
        if (existing != null) {
            existing.cancel(false);
        }

        // 경광등 ON
        connection.lightOn();

        // 자동 OFF 타이머 스케줄
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            connection.lightOff();
            activeTimers.remove(connection);
        }, autoOffDelayMs, TimeUnit.MILLISECONDS);

        activeTimers.put(connection, future);
    }

    /**
     * 부저 ON + 자동 OFF 스케줄.
     * 같은 리더기에서 재호출 시 타이머를 리셋한다.
     */
    public void triggerBuzzer(ReaderConnection connection) {
        // 기존 타이머가 있으면 취소
        ScheduledFuture<?> existing = buzzerTimers.get(connection);
        if (existing != null) {
            existing.cancel(false);
        }

        // 부저 ON
        connection.buzzerOn();

        // 자동 OFF 타이머 스케줄
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            connection.buzzerOff();
            buzzerTimers.remove(connection);
        }, autoOffDelayMs, TimeUnit.MILLISECONDS);

        buzzerTimers.put(connection, future);
    }

    public void shutdown() {
        // 모든 활성 타이머 취소
        for (ScheduledFuture<?> future : activeTimers.values()) {
            future.cancel(false);
        }
        activeTimers.clear();
        for (ScheduledFuture<?> future : buzzerTimers.values()) {
            future.cancel(false);
        }
        buzzerTimers.clear();
        scheduler.shutdownNow();
        AppLogger.info("WarningLightController", "Shutdown complete");
    }
}
