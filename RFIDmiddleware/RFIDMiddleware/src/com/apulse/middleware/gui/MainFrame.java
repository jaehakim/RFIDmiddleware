package com.apulse.middleware.gui;

import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.reader.ReaderConnection;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.reader.ReaderStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {
    private static final String CONFIG_FILE = "config" + File.separator + "readers.cfg";

    private final ReaderManager readerManager;
    private final ReaderStatusPanel statusPanel;
    private final TagDataPanel tagDataPanel;
    private final LogPanel logPanel;

    private List<ReaderConfig> configs;

    public MainFrame() {
        super("RFID 미들웨어");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null);

        readerManager = new ReaderManager();
        statusPanel = new ReaderStatusPanel();
        tagDataPanel = new TagDataPanel();
        logPanel = new LogPanel();

        initLayout();
        loadConfig();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(
                    MainFrame.this,
                    "프로그램을 종료하시겠습니까?",
                    "종료 확인",
                    JOptionPane.YES_NO_OPTION
                );
                if (result == JOptionPane.YES_OPTION) {
                    logPanel.appendLog("Shutting down...");
                    readerManager.shutdown();
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void initLayout() {
        setLayout(new BorderLayout(0, 2));

        // 상단 툴바
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JButton connectAllBtn = createToolButton("전체 연결", e -> {
            logPanel.appendLog("Connect all readers...");
            readerManager.connectAll();
        });
        JButton disconnectAllBtn = createToolButton("전체 해제", e -> {
            logPanel.appendLog("Disconnect all readers...");
            readerManager.disconnectAll();
        });
        JButton startInvBtn = createToolButton("인벤토리 시작", e -> {
            logPanel.appendLog("Start inventory all...");
            readerManager.startInventoryAll();
        });
        JButton stopInvBtn = createToolButton("인벤토리 중지", e -> {
            logPanel.appendLog("Stop inventory all...");
            readerManager.stopInventoryAll();
        });
        JButton configBtn = createToolButton("설정", e -> openConfigDialog());
        JButton clearTagsBtn = createToolButton("태그 초기화", e -> tagDataPanel.clearTags());

        toolBar.add(connectAllBtn);
        toolBar.addSeparator(new Dimension(5, 0));
        toolBar.add(disconnectAllBtn);
        toolBar.addSeparator(new Dimension(15, 0));
        toolBar.add(startInvBtn);
        toolBar.addSeparator(new Dimension(5, 0));
        toolBar.add(stopInvBtn);
        toolBar.addSeparator(new Dimension(15, 0));
        toolBar.add(clearTagsBtn);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(configBtn);

        add(toolBar, BorderLayout.NORTH);

        // 중앙: 상태 패널 + 태그 테이블 + 로그
        JPanel centerPanel = new JPanel(new BorderLayout(0, 2));
        centerPanel.add(statusPanel, BorderLayout.NORTH);

        // 태그 테이블과 로그를 분할
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tagDataPanel, logPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(5);

        centerPanel.add(splitPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JButton createToolButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.addActionListener(action);
        return btn;
    }

    /** 설정 파일 로드 및 리더기 초기화 */
    private void loadConfig() {
        configs = ReaderConfig.loadFromFile(CONFIG_FILE);
        logPanel.appendLog("Config loaded: " + configs.size() + " reader(s)");

        initializeReaders();
    }

    /** 리더기 매니저 및 상태 패널 초기화 */
    private void initializeReaders() {
        ReaderConnection.ReaderConnectionListener statusListener = new ReaderConnection.ReaderConnectionListener() {
            @Override
            public void onStatusChanged(ReaderConnection connection, ReaderStatus oldStatus, ReaderStatus newStatus) {
                SwingUtilities.invokeLater(() -> {
                    int index = findConnectionIndex(connection);
                    if (index >= 0) {
                        statusPanel.updateStatus(index, newStatus);
                    }
                });
            }

            @Override
            public void onLightChanged(ReaderConnection connection, boolean lightOn) {
                SwingUtilities.invokeLater(() -> {
                    int index = findConnectionIndex(connection);
                    if (index >= 0) {
                        statusPanel.updateLightStatus(index, lightOn);
                    }
                });
            }

            @Override
            public void onLog(ReaderConnection connection, String message) {
                SwingUtilities.invokeLater(() ->
                    logPanel.appendLog(connection.getConfig().getName(), message)
                );
            }
        };

        ReaderConnection.TagDataListener tagListener = (connection, epc, rssi) -> {
            SwingUtilities.invokeLater(() ->
                tagDataPanel.addTag(connection.getConfig().getName(), epc, rssi)
            );
        };

        readerManager.initialize(configs, statusListener, tagListener);
        statusPanel.initialize(configs, readerManager);
    }

    /** ReaderConnection의 인덱스 찾기 */
    private int findConnectionIndex(ReaderConnection connection) {
        List<ReaderConnection> connections = readerManager.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i) == connection) return i;
        }
        return -1;
    }

    /** 설정 다이얼로그 열기 */
    private void openConfigDialog() {
        ConfigDialog dialog = new ConfigDialog(this, configs);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            // 기존 연결 정리
            readerManager.shutdown();

            configs = dialog.getConfigs();
            ReaderConfig.saveToFile(CONFIG_FILE, configs);
            logPanel.appendLog("Config saved: " + configs.size() + " reader(s)");

            // 재초기화
            initializeReaders();
        }
    }
}
