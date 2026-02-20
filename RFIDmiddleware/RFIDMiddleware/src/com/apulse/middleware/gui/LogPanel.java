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
        setBorder(BorderFactory.createTitledBorder("로그"));
        setPreferredSize(new Dimension(0, 150));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(200, 200, 200));
        logArea.setCaretColor(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);

        // 하단 버튼
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton saveButton = new JButton("로그 저장");
        saveButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        saveButton.addActionListener(e -> saveLog());
        buttonPanel.add(saveButton);

        JButton clearButton = new JButton("로그 지우기");
        clearButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        clearButton.addActionListener(e -> logArea.setText(""));
        buttonPanel.add(clearButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /** 로그 메시지 추가 */
    public void appendLog(String message) {
        String timestamp = HexUtils.now();
        String logLine = "[" + timestamp + "] " + message + "\n";
        logArea.append(logLine);

        // 최대 라인 수 제한
        int lineCount = logArea.getLineCount();
        if (lineCount > MAX_LOG_LINES) {
            try {
                int end = logArea.getLineEndOffset(lineCount - MAX_LOG_LINES);
                logArea.replaceRange("", 0, end);
            } catch (Exception ignored) {}
        }

        // 자동 스크롤
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** 로그를 파일로 저장 */
    private void saveLog() {
        String text = logArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "저장할 로그가 없습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String defaultName = "log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("로그 저장");
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("텍스트 파일 (*.txt)", "txt"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.print(text);
                JOptionPane.showMessageDialog(this, "로그가 저장되었습니다.\n" + file.getAbsolutePath(), "저장 완료", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "저장 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 리더기별 로그 */
    public void appendLog(String readerName, String message) {
        appendLog("[" + readerName + "] " + message);
    }
}
