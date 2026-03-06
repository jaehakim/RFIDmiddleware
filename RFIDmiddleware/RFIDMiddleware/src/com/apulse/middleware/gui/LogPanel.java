package com.apulse.middleware.gui;

import com.apulse.middleware.config.LogConfig;
import com.apulse.middleware.util.HexUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogPanel extends JPanel {
    private final JTextArea logArea;
    private static final int MAX_LOG_LINES = 1000;

    private boolean uiFileEnabled = false;
    private String uiFilePath;
    private long uiMaxSizeBytes;
    private int uiMaxCount;
    private final Object fileLock = new Object();

    public LogPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.CONTENT_BG);
        setPreferredSize(new Dimension(0, 150));

        // Section label with help button
        add(Theme.createSectionHeader("[ \ub85c\uadf8 ]",
            "\ub85c\uadf8 - \ub3c4\uc6c0\ub9d0",
            "\u25a0 \ub85c\uadf8 \ud328\ub110\n\n"
            + "\ubbf8\ub4e4\uc6e8\uc5b4 \ub3d9\uc791 \uc0c1\ud669\uc744 \uc2e4\uc2dc\uac04\uc73c\ub85c \ud45c\uc2dc\ud569\ub2c8\ub2e4.\n\n"
            + "\u25b6 \ud45c\uc2dc \ub0b4\uc6a9\n"
            + "  \u2022 \ub9ac\ub354\uae30 \uc5f0\uacb0/\ud574\uc81c \uc0c1\ud0dc \ubcc0\uacbd\n"
            + "  \u2022 \uc778\ubca4\ud1a0\ub9ac \uc2dc\uc791/\uc911\uc9c0 \uc774\ubca4\ud2b8\n"
            + "  \u2022 \ubbf8\ud5c8\uac00 \ubc18\ucd9c \uac10\uc9c0 \uc54c\ub9bc (EPC, \uc790\uc0b0\ubc88\ud638, \ub9ac\ub354\uae30\uba85)\n"
            + "  \u2022 \uacbd\uad11\ub4f1/\ubd80\uc800 \ub3d9\uc791 \uc0c1\ud0dc\n"
            + "  \u2022 DB \uc5f0\uacb0 \uc0c1\ud0dc \ubc0f \uc624\ub958\n"
            + "  \u2022 API \uc11c\ubc84 \uc694\uccad/\uc751\ub2f5 \ub85c\uadf8\n\n"
            + "\u25b6 \ub85c\uadf8 \ud615\uc2dd\n"
            + "  [\uc2dc\uac04] [\ub9ac\ub354\uae30\uba85] \uba54\uc2dc\uc9c0\n\n"
            + "\u25b6 \ud558\ub2e8 \ubc84\ud2bc\n"
            + "  \u2022 \ub85c\uadf8 \uc800\uc7a5: \ud604\uc7ac \ub85c\uadf8\ub97c .txt \ud30c\uc77c\ub85c \uc800\uc7a5\n"
            + "  \u2022 \ub85c\uadf8 \uc9c0\uc6b0\uae30: \ud654\uba74\uc758 \ub85c\uadf8 \ub0b4\uc6a9 \uc804\uccb4 \uc0ad\uc81c\n\n"
            + "\u25b6 \uc790\ub3d9 \uad00\ub9ac\n"
            + "  \u2022 \ud654\uba74 \ub85c\uadf8: \ucd5c\ub300 1,000\uc904 \uc720\uc9c0 (\ucd08\uacfc \uc2dc \uc624\ub798\ub41c \ud56d\ubaa9 \uc790\ub3d9 \uc0ad\uc81c)\n"
            + "  \u2022 \ud30c\uc77c \ub85c\uadf8: log.cfg \uc124\uc815\uc5d0 \ub530\ub77c \uc790\ub3d9 \ub85c\ud14c\uc774\uc158\n"
            + "    (\uae30\ubcf8: 10MB \ub2e8\uc704, \ucd5c\ub300 5\uac1c \ud30c\uc77c \ubcf4\uad00)\n\n"
            + "\u25b6 \uae30\ubcf8 \ub85c\uadf8 \ud30c\uc77c \uc704\uce58\n"
            + "  \u2022 \uc2dc\uc2a4\ud15c \ub85c\uadf8: logs/middleware.log\n"
            + "  \u2022 UI \ub85c\uadf8: logs/ui.log\n"
            + "  (\uc2e4\ud589 \ub514\ub809\ud1a0\ub9ac \uae30\uc900 \uc0c1\ub300\uacbd\ub85c, config/log.cfg\uc5d0\uc11c \ubcc0\uacbd \uac00\ub2a5)"
        ), BorderLayout.NORTH);

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

    public void initFileLogging(LogConfig logConfig) {
        if (logConfig != null && logConfig.isUiFileEnabled()) {
            this.uiFileEnabled = true;
            this.uiFilePath = logConfig.getUiFilePath();
            this.uiMaxSizeBytes = (long) logConfig.getUiMaxSizeMB() * 1024 * 1024;
            this.uiMaxCount = logConfig.getUiMaxCount();

            File logFile = new File(uiFilePath);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        }
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

        if (uiFileEnabled) {
            writeToFile(logLine);
        }
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

    private void writeToFile(String logLine) {
        synchronized (fileLock) {
            try {
                File logFile = new File(uiFilePath);
                if (logFile.exists() && logFile.length() >= uiMaxSizeBytes) {
                    rotateFiles();
                }
                try (FileWriter fw = new FileWriter(logFile, true)) {
                    fw.write(logLine);
                }
            } catch (Exception e) {
                // silently ignore file write errors
            }
        }
    }

    private void rotateFiles() {
        File oldest = new File(uiFilePath + "." + (uiMaxCount - 1));
        if (oldest.exists()) oldest.delete();

        for (int i = uiMaxCount - 2; i >= 0; i--) {
            File src = new File(uiFilePath + (i == 0 ? "" : "." + i));
            File dst = new File(uiFilePath + "." + (i + 1));
            if (src.exists()) {
                try {
                    Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }
}
