package com.apulse.middleware.reader;

import java.awt.Color;

public enum ReaderStatus {
    DISCONNECTED("미연결", new Color(180, 180, 180)),
    CONNECTING("연결 중", new Color(255, 200, 0)),
    CONNECTED("연결됨", new Color(0, 180, 0)),
    READING("읽기 중", new Color(0, 120, 255)),
    ERROR("오류", new Color(220, 0, 0));

    private final String label;
    private final Color color;

    ReaderStatus(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() { return label; }
    public Color getColor() { return color; }
}
