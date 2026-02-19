package com.apulse.middleware.reader;

import com.apulse.middleware.config.ReaderConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderManager {
    private final List<ReaderConnection> connections = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public List<ReaderConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    /** 설정 목록으로 ReaderConnection 초기화 */
    public void initialize(List<ReaderConfig> configs,
                           ReaderConnection.ReaderConnectionListener statusListener,
                           ReaderConnection.TagDataListener tagListener) {
        // 기존 연결 정리
        disconnectAll();
        connections.clear();

        for (ReaderConfig cfg : configs) {
            ReaderConnection conn = new ReaderConnection(cfg);
            conn.addListener(statusListener);
            conn.addTagListener(tagListener);
            connections.add(conn);
        }
    }

    /** 전체 리더기 연결 */
    public void connectAll() {
        for (ReaderConnection conn : connections) {
            executor.submit(() -> conn.connect());
        }
    }

    /** 전체 리더기 연결 해제 */
    public void disconnectAll() {
        for (ReaderConnection conn : connections) {
            executor.submit(() -> conn.disconnect());
        }
    }

    /** 전체 인벤토리 시작 */
    public void startInventoryAll() {
        for (ReaderConnection conn : connections) {
            if (conn.getStatus() == ReaderStatus.CONNECTED) {
                executor.submit(() -> conn.startInventory());
            }
        }
    }

    /** 전체 인벤토리 중지 */
    public void stopInventoryAll() {
        for (ReaderConnection conn : connections) {
            if (conn.getStatus() == ReaderStatus.READING) {
                executor.submit(() -> conn.stopInventory());
            }
        }
    }

    /** 개별 리더기 연결 */
    public void connect(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).connect());
        }
    }

    /** 개별 리더기 해제 */
    public void disconnect(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).disconnect());
        }
    }

    /** 개별 인벤토리 시작 */
    public void startInventory(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).startInventory());
        }
    }

    /** 개별 인벤토리 중지 */
    public void stopInventory(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).stopInventory());
        }
    }

    /** 개별 경광등 ON */
    public void lightOn(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).lightOn());
        }
    }

    /** 개별 경광등 OFF */
    public void lightOff(int index) {
        if (index >= 0 && index < connections.size()) {
            executor.submit(() -> connections.get(index).lightOff());
        }
    }

    /** 종료 시 정리 */
    public void shutdown() {
        for (ReaderConnection conn : connections) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        executor.shutdownNow();
    }
}
