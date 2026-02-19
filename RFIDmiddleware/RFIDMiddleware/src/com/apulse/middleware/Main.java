package com.apulse.middleware;

import com.apulse.middleware.gui.MainFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Swing Look & Feel 설정
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // GUI 스레드에서 실행
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
