package com.apulse.middleware.gui;

import com.apulse.middleware.util.HexUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogPanel extends JPanel {
    private final JTextArea logArea;
    private static final int MAX_LOG_LINES = 1000;

    public LogPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.CONTENT_BG);
        setPreferredSize(new Dimension(0, 150));

        // Section label instead of TitledBorder
        add(Theme.createSectionLabel("\ub85c\uadf8"), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(Theme.LOG);
        logArea.setBackground(Theme.LOG_BG);
        logArea.setForeground(Theme.LOG_FG);
        logArea.setCaretColor(new Color(0x88, 0x88, 0x88));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttonPanel.setOpaque(true);
        buttonPanel.setBackground(Theme.CONTENT_BG);

        JButton saveButton = Theme.createFlatButton("\ub85c\uadf8 \uc800\uc7a5", e -> saveLog());
        JButton clearButton = Theme.createFlatButton("\ub85c\uadf8 \uc9c0\uc6b0\uae30", e -> logArea.setText(""));

        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void appendLog(String message) {
        String timestamp = HexUtils.now();
        String logLine = "[" + timestamp + "] " + message + "\n";
        logArea.append(logLine);

        int lineCount = logArea.getLineCount();
        if (lineCount > MAX_LOG_LINES) {
            try {
                int end = logArea.getLineEndOffset(lineCount - MAX_LOG_LINES);
                logArea.replaceRange("", 0, end);
            } catch (Exception ignored) {}
        }

        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void saveLog() {
        String text = logArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "\uc800\uc7a5\ud560 \ub85c\uadf8\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.", "\uc54c\ub9bc", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String defaultName = "log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("\ub85c\uadf8 \uc800\uc7a5");
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("\ud14d\uc2a4\ud2b8 \ud30c\uc77c (*.txt)", "txt"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.print(text);
                JOptionPane.showMessageDialog(this, "\ub85c\uadf8\uac00 \uc800\uc7a5\ub418\uc5c8\uc2b5\ub2c8\ub2e4.\n" + file.getAbsolutePath(), "\uc800\uc7a5 \uc644\ub8cc", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "\uc800\uc7a5 \uc2e4\ud328: " + ex.getMessage(), "\uc624\ub958", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void appendLog(String readerName, String message) {
        appendLog("[" + readerName + "] " + message);
    }
}
