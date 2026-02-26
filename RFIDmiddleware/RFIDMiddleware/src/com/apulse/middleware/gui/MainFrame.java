package com.apulse.middleware.gui;

import com.apulse.middleware.api.ApiServer;
import com.apulse.middleware.config.DatabaseConfig;
import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.db.AssetRepository;
import com.apulse.middleware.db.DatabaseManager;
import com.apulse.middleware.db.TagRepository;
import com.apulse.middleware.reader.ReaderConnection;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.reader.ReaderStatus;
import com.apulse.middleware.reader.WarningLightController;
import com.apulse.middleware.util.AppLogger;
import com.apulse.middleware.util.HexUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainFrame extends JFrame {
    private static final String CONFIG_FILE = "config" + File.separator + "readers.cfg";

    private final ReaderManager readerManager;
    private final ReaderStatusPanel statusPanel;
    private final TagDataPanel tagDataPanel;
    private final LogPanel logPanel;

    private ApiServer apiServer;
    private List<ReaderConfig> configs;

    public MainFrame() {
        super("RFID \ubbf8\ub4e4\uc6e8\uc5b4");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null);

        // DB/cache init
        DatabaseConfig dbConfig = new DatabaseConfig();
        DatabaseManager.getInstance().initialize(dbConfig);
        TagRepository.getInstance().start();
        AssetRepository.getInstance().start(30);

        readerManager = new ReaderManager();
        statusPanel = new ReaderStatusPanel();
        tagDataPanel = new TagDataPanel(dbConfig.getCacheTtlSeconds(), dbConfig.getCacheMaxSize());
        logPanel = new LogPanel();

        initLayout();
        loadConfig();

        // REST API server
        try {
            apiServer = new ApiServer(readerManager, configs, CONFIG_FILE);
            apiServer.start();
        } catch (Exception e) {
            AppLogger.error("MainFrame", "API Server start failed: " + e.getMessage());
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(
                    MainFrame.this,
                    "\ud504\ub85c\uadf8\ub7a8\uc744 \uc885\ub8cc\ud558\uc2dc\uaca0\uc2b5\ub2c8\uae4c?",
                    "\uc885\ub8cc \ud655\uc778",
                    JOptionPane.YES_NO_OPTION
                );
                if (result == JOptionPane.YES_OPTION) {
                    logPanel.appendLog("Shutting down...");
                    if (apiServer != null) apiServer.shutdown();
                    readerManager.shutdown();
                    WarningLightController.getInstance().shutdown();
                    AssetRepository.getInstance().shutdown();
                    TagRepository.getInstance().shutdown();
                    DatabaseManager.getInstance().shutdown();
                    AppLogger.shutdown();
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void initLayout() {
        setLayout(new BorderLayout(0, 0));

        // --- Dark navy header panel ---
        JPanel headerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint gp = new GradientPaint(
                    0, 0, Theme.HEADER_BG,
                    0, getHeight(), Theme.HEADER_BG_BOTTOM);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        headerPanel.setPreferredSize(new Dimension(0, Theme.HEADER_H));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        headerPanel.setOpaque(false);

        // Left: title with accent background
        JPanel titlePanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillRoundRect(0, 6, getWidth() - 4, getHeight() - 12, 8, 8);
                g2.dispose();
            }
        };
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel("RFID \ubbf8\ub4e4\uc6e8\uc5b4");
        titleLabel.setFont(Theme.TITLE);
        titleLabel.setForeground(new Color(0x8A, 0xBE, 0xF5));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        titlePanel.add(titleLabel);
        headerPanel.add(titlePanel, BorderLayout.WEST);

        // Center: button groups
        JPanel centerButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        centerButtons.setOpaque(false);

        JButton connectAllBtn = Theme.createHeaderButton("전체 연결", Theme.createHeaderIcon("connect"), e -> {
            logPanel.appendLog("Connect all readers...");
            readerManager.connectAll();
        });
        JButton disconnectAllBtn = Theme.createHeaderButton("전체 해제", Theme.createHeaderIcon("disconnect"), e -> {
            logPanel.appendLog("Disconnect all readers...");
            readerManager.disconnectAll();
        });
        JButton startInvBtn = Theme.createHeaderButton("인벤토리 시작", Theme.createHeaderIcon("play"), e -> {
            logPanel.appendLog("Start inventory all...");
            readerManager.startInventoryAll();
        });
        JButton stopInvBtn = Theme.createHeaderButton("인벤토리 중지", Theme.createHeaderIcon("stop"), e -> {
            logPanel.appendLog("Stop inventory all...");
            readerManager.stopInventoryAll();
        });
        JButton clearTagsBtn = Theme.createHeaderButton("태그 초기화", Theme.createHeaderIcon("clear"), e -> tagDataPanel.clearTags());
        JButton assetDbBtn = Theme.createHeaderButton("자산 DB", Theme.createHeaderIcon("database"), e -> showAssetDbDialog());

        centerButtons.add(connectAllBtn);
        centerButtons.add(disconnectAllBtn);
        centerButtons.add(createHeaderSeparator());
        centerButtons.add(startInvBtn);
        centerButtons.add(stopInvBtn);
        centerButtons.add(createHeaderSeparator());
        centerButtons.add(clearTagsBtn);
        centerButtons.add(assetDbBtn);

        headerPanel.add(centerButtons, BorderLayout.CENTER);

        // Right: help + settings
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.setOpaque(false);

        JButton helpBtn = Theme.createHeaderButton("도움말", Theme.createHeaderIcon("help"), e -> showHelpDialog());
        JButton flowBtn = Theme.createHeaderButton("흐름도", Theme.createHeaderIcon("flow"), e -> openFlowDiagram());
        JButton configBtn = Theme.createHeaderButton("설정", Theme.createHeaderIcon("settings"), e -> openConfigDialog());

        rightButtons.add(helpBtn);
        rightButtons.add(flowBtn);
        rightButtons.add(configBtn);

        headerPanel.add(rightButtons, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // --- Content area ---
        JPanel contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setBackground(Theme.CONTENT_BG);
        contentPanel.setOpaque(true);

        contentPanel.add(statusPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tagDataPanel, logPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);

        contentPanel.add(splitPane, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);
    }

    /** Create a vertical separator for the header */
    private Component createHeaderSeparator() {
        JPanel sep = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.HEADER_SEPARATOR);
                int midX = getWidth() / 2;
                g.drawLine(midX, 8, midX, getHeight() - 8);
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(12, Theme.HEADER_H));
        return sep;
    }

    private void loadConfig() {
        configs = ReaderConfig.loadFromFile(CONFIG_FILE);
        logPanel.appendLog("Config loaded: " + configs.size() + " reader(s)");
        initializeReaders();
    }

    private void initializeReaders() {
        ReaderConnection.ReaderConnectionListener statusListener = new ReaderConnection.ReaderConnectionListener() {
            @Override
            public void onStatusChanged(ReaderConnection connection, ReaderStatus oldStatus, ReaderStatus newStatus) {
                SwingUtilities.invokeLater(() -> {
                    int index = findConnectionIndex(connection);
                    if (index >= 0) {
                        statusPanel.updateStatus(index, newStatus);
                        // Beep icon active state depends on READING status
                        statusPanel.updateBeepEnabled(index, connection.getConfig().isBeepEnabled());
                    }
                    if (newStatus == ReaderStatus.CONNECTED) {
                        saveConfig();
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
            public void onBuzzerChanged(ReaderConnection connection, boolean buzzerOn) {
                SwingUtilities.invokeLater(() -> {
                    int index = findConnectionIndex(connection);
                    if (index >= 0) {
                        statusPanel.updateBuzzerStatus(index, buzzerOn);
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

        ReaderConnection.TagDataListener tagListener = (connection, epc, rssi, antenna) -> {
            String mask = ReaderConfig.getEpcMask();
            if (!mask.isEmpty() && !epc.toUpperCase().startsWith(mask.toUpperCase())) {
                SwingUtilities.invokeLater(() ->
                    logPanel.appendLog(connection.getConfig().getName(),
                        "MASK filtered: EPC=" + epc + " (mask=" + mask + ")")
                );
                return;
            }

            AssetRepository.AssetInfo assetInfo = AssetRepository.getInstance().getAssetInfo(epc);
            AssetRepository.AssetInfo unauthorizedAsset = AssetRepository.getInstance().checkUnauthorizedExport(epc);

            String assetStatus = null;
            if (assetInfo != null) {
                assetStatus = (unauthorizedAsset != null)
                    ? TagDataPanel.STATUS_ALERT
                    : TagDataPanel.STATUS_PERMITTED;
            }

            final String finalStatus = assetStatus;
            SwingUtilities.invokeLater(() -> {
                String assetNumber = assetInfo != null ? assetInfo.getAssetNumber() : null;
                String assetName = assetInfo != null ? assetInfo.getAssetName() : null;
                String department = assetInfo != null ? assetInfo.getDepartment() : null;

                boolean isNew = tagDataPanel.addTag(
                    connection.getConfig().getName(), epc, rssi, antenna,
                    assetNumber, assetName, department, finalStatus);
                if (isNew) {
                    String readTime = HexUtils.nowShort();
                    TagRepository.getInstance().insertTagRead(
                        epc, connection.getConfig().getName(),
                        rssi, antenna, readTime);
                    TagRepository.getInstance().addRecentTag(
                        readTime, connection.getConfig().getName(), epc, rssi, antenna,
                        assetNumber, assetName, department, finalStatus);
                }

                if (unauthorizedAsset != null && AssetRepository.getInstance().shouldAlert(epc)) {
                    String time = HexUtils.nowShort();
                    String readerName = connection.getConfig().getName();

                    if (connection.getConfig().isWarningLightEnabled()) {
                        WarningLightController.getInstance().triggerWarningLight(connection);
                    }

                    if (connection.getConfig().isBuzzerEnabled()) {
                        WarningLightController.getInstance().triggerBuzzer(connection);
                    }

                    AssetRepository.getInstance().insertAlert(
                        epc, unauthorizedAsset.getAssetNumber(), readerName, rssi, time);

                    logPanel.appendLog(readerName,
                        "UNAUTHORIZED EXPORT: EPC=" + epc
                        + ", Asset=" + unauthorizedAsset.getAssetNumber()
                        + " (" + (unauthorizedAsset.getAssetName() != null ? unauthorizedAsset.getAssetName() : "") + ")");
                }
            });
        };

        readerManager.initialize(configs, statusListener, tagListener);
        statusPanel.initialize(configs, readerManager, this::saveConfig);

        // Pass config state (buzzer/light/beep) to icons
        for (int i = 0; i < configs.size(); i++) {
            ReaderConfig cfg = configs.get(i);
            statusPanel.updateConfigState(i,
                cfg.isBuzzerEnabled(), cfg.isWarningLightEnabled(), cfg.isBeepEnabled());
        }
    }

    private int findConnectionIndex(ReaderConnection connection) {
        List<ReaderConnection> connections = readerManager.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i) == connection) return i;
        }
        return -1;
    }

    private void saveConfig() {
        ReaderConfig.saveToFile(CONFIG_FILE, configs);
    }

    private void showHelpDialog() {
        String html = "<html><body style='font-family:\ub9d1\uc740 \uace0\ub515,sans-serif; width:450px; padding:4px;'>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px;'>\ub9ac\ub354\uae30 \uc0c1\ud0dc</h2>"

            + "<h3>\uc0c1\ud0dc \uc0c9\uc0c1</h3>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b style='color:#B4B4B4;'>&#9632;</b></td>"
            +     "<td><b>\ubbf8\uc5f0\uacb0</b></td><td>\ub9ac\ub354\uae30\uc640 \uc5f0\uacb0\ub418\uc9c0 \uc54a\uc740 \ucd08\uae30 \uc0c1\ud0dc</td></tr>"
            + "<tr><td><b style='color:#FFC800;'>&#9632;</b></td>"
            +     "<td><b>\uc5f0\uacb0 \uc911</b></td><td>\ub9ac\ub354\uae30\uc5d0 TCP \uc5f0\uacb0\uc744 \uc2dc\ub3c4\ud558\ub294 \uc911</td></tr>"
            + "<tr><td><b style='color:#00B400;'>&#9632;</b></td>"
            +     "<td><b>\uc5f0\uacb0\ub428</b></td><td>\ub9ac\ub354\uae30\uc640 \uc815\uc0c1 \uc5f0\uacb0\ub41c \uc0c1\ud0dc (\uc548\ud14c\ub098 \uc124\uc815 \uc790\ub3d9 \uc218\uc2e0 \uc644\ub8cc)</td></tr>"
            + "<tr><td><b style='color:#0078FF;'>&#9632;</b></td>"
            +     "<td><b>\uc77d\uae30 \uc911</b></td><td>\uc778\ubca4\ud1a0\ub9ac \uc2e4\ud589 \uc911 (\ud0dc\uadf8 \uc77d\uae30 \ub3d9\uc791, \uc810\uba78)</td></tr>"
            + "<tr><td><b style='color:#DC0000;'>&#9632;</b></td>"
            +     "<td><b>\uc624\ub958</b></td><td>\uc5f0\uacb0 \uc2e4\ud328 \ub610\ub294 \ud1b5\uc2e0 \uc624\ub958 \ubc1c\uc0dd</td></tr>"
            + "</table>"

            + "<h3>\uc544\uc774\ucf58 \ud45c\uc2dc</h3>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td>\uc88c\uce21 \uc0c9\uc0c1 \ubc14</td><td>\ud604\uc7ac \ub9ac\ub354\uae30 \uc0c1\ud0dc\ub97c \uc0c9\uc0c1\uc73c\ub85c \ud45c\uc2dc</td></tr>"
            + "</table>"

            + "<h3>\uc778\ub514\ucf00\uc774\ud130 (\uc6b0\uce21 3\uac1c)</h3>"
            + "<p style='font-size:11px; margin:2px 0 4px 0;'>\uc704\uc5d0\uc11c \uc544\ub798\ub85c: \ubd80\uc800 / \uacbd\uad11\ub4f1 / \ube44\ud504\uc74c</p>"
            + "<table cellpadding='4' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>\ubaa8\uc591</th><th>\uc0c1\ud0dc</th><th>\uc124\uba85</th></tr>"
            + "<tr><td style='color:#C8CCD4;'><b>&#9633;</b> \ud68c\uc0c9 \ud14c\ub450\ub9ac \uc0ac\uac01\ud615</td>"
            +     "<td><b>\ubbf8\uc0ac\uc6a9</b></td><td>\uc124\uc815\uc5d0\uc11c \uae30\ub2a5 OFF</td></tr>"
            + "<tr><td><b style='color:#2ECC71;'>&#9679;</b> / <b style='color:#1E96DC;'>&#9679;</b> / <b style='color:#FFB400;'>&#9679;</b> \uceec\ub7ec \uc6d0\ud615</td>"
            +     "<td><b>\uc0ac\uc6a9</b></td><td>\uc124\uc815\uc5d0\uc11c \uae30\ub2a5 ON (\ub300\uae30 \uc911)</td></tr>"
            + "<tr><td><b style='color:#2ECC71;'>&#10687;</b> / <b style='color:#1E96DC;'>&#10687;</b> / <b style='color:#FFB400;'>&#10687;</b> \uae5c\ubc15\uc784</td>"
            +     "<td><b>\ud65c\uc131\ud654</b></td><td>\uc774\ubca4\ud2b8 \ubc1c\uc0dd \uc911 (\uc2e4\uc81c \ub3d9\uc791 \uc911)</td></tr>"
            + "</table>"
            + "<table cellpadding='3' cellspacing='0' border='0' style='margin-top:4px;'>"
            + "<tr><td><b style='color:#2ECC71;'>&#9679;</b> \ube44\ud504\uc74c</td>"
            +     "<td>\ub179\uc0c9 &mdash; \uc778\ubca4\ud1a0\ub9ac(\uc77d\uae30 \uc911) \uc0c1\ud0dc\uc5d0\uc11c \ud65c\uc131\ud654</td></tr>"
            + "<tr><td><b style='color:#1E96DC;'>&#9679;</b> \ubd80\uc800</td>"
            +     "<td>\ud30c\ub780\uc0c9 &mdash; \ubc18\ucd9c\uc54c\ub9bc \uc2dc \ub9b4\ub808\uc774 ON\uc73c\ub85c \ud65c\uc131\ud654</td></tr>"
            + "<tr><td><b style='color:#FFB400;'>&#9679;</b> \uacbd\uad11\ub4f1</td>"
            +     "<td>\uc8fc\ud669\uc0c9 &mdash; \ubc18\ucd9c\uc54c\ub9bc \uc2dc \ub9b4\ub808\uc774 ON\uc73c\ub85c \ud65c\uc131\ud654</td></tr>"
            + "</table>"

            + "<h3>\uc6b0\ud074\ub9ad \uba54\ub274</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc5f0\uacb0 / \uc5f0\uacb0 \ud574\uc81c</b></td><td>\uac1c\ubcc4 \ub9ac\ub354\uae30 TCP \uc5f0\uacb0 \uc81c\uc5b4</td></tr>"
            + "<tr><td><b>\uc778\ubca4\ud1a0\ub9ac \uc2dc\uc791 / \uc911\uc9c0</b></td><td>\ud0dc\uadf8 \uc77d\uae30 \ub3d9\uc791 \uc81c\uc5b4</td></tr>"
            + "<tr><td><b>\uacbd\uad11\ub4f1 ON / OFF</b></td><td>\ub9ac\ub354\uae30 \ub9b4\ub808\uc774(\uacbd\uad11\ub4f1) \uc81c\uc5b4</td></tr>"
            + "<tr><td><b>\ubd80\uc800 ON / OFF</b></td><td>\ub9ac\ub354\uae30 \ubd80\uc800 \uc81c\uc5b4</td></tr>"
            + "<tr><td><b>\uc548\ud14c\ub098 \uc124\uc815</b></td><td>\uc548\ud14c\ub098 \ucd9c\ub825(dBm) \ubc0f \ub4dc\uc6f0\uc2dc\uac04(ms) \uc124\uc815</td></tr>"
            + "</table>"

            + "<h3>\uc5f0\uacb0 \uc2dc \uc124\uc815 \ub3d9\uc791</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc548\ud14c\ub098/\ub4dc\uc6f0</b></td><td>\ub9ac\ub354\uae30\uc758 \uc2e4\uc81c \uc124\uc815\uac12\uc744 \uc790\ub3d9\uc73c\ub85c \uac00\uc838\uc640 \ud45c\uc2dc (FETCH)</td></tr>"
            + "<tr><td><b>\uacbd\uad11\ub4f1/\ubd80\uc800</b></td><td>\ubc18\ucd9c\uc54c\ub9bc \uc774\ubca4\ud2b8 \ubc1c\uc0dd \uc2dc\uc5d0\ub9cc \ub3d9\uc791 (\uc774\ubca4\ud2b8 \uae30\ubc18)</td></tr>"
            + "<tr><td><b>\uc124\uc815 \uc800\uc7a5</b></td><td>\uac00\uc838\uc628 \uc124\uc815\uac12\uc774 readers.cfg\uc5d0 \uc790\ub3d9 \uc800\uc7a5\ub428</td></tr>"
            + "</table>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>\ubc18\ucd9c\uc54c\ub9bc (\uacbd\uad11\ub4f1 + \ubd80\uc800)</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>\ub3d9\uc791 \uc870\uac74</b></td><td>\ubc18\ucd9c\uc54c\ub9bc \uac10\uc9c0 + \ub9ac\ub354\uae30 \uc124\uc815\uc5d0\uc11c \uacbd\uad11\ub4f1/\ubd80\uc800 '\uc801\uc6a9'(1)</td></tr>"
            + "<tr><td><b>\uacbd\uad11\ub4f1</b></td><td>\ub9b4\ub808\uc774 1\ubc88 = <b style='color:#DC0000;'>\ube68\uac04\ub4f1</b> (5\ucd08 \uc790\ub3d9 \uc18c\ub4f1)</td></tr>"
            + "<tr><td><b>\ubd80\uc800</b></td><td>\ub9b4\ub808\uc774 2\ubc88 = <b style='color:#1E96DC;'>\ubd80\uc800</b> (5\ucd08 \uc790\ub3d9 OFF)</td></tr>"
            + "<tr><td><b>\uc7ac\uac10\uc9c0 \uc2dc</b></td><td>\uac01\uac01 \ud0c0\uc774\uba38 \ub9ac\uc14b (5\ucd08 \uc7ac\uc2dc\uc791)</td></tr>"
            + "<tr><td><b>\uc54c\ub9bc \uc911\ubcf5\uc81c\uac70</b></td><td>\uac19\uc740 EPC 30\ucd08 \ub0b4 \uc7ac\uc54c\ub9bc \ubc29\uc9c0</td></tr>"
            + "</table>"

            + "<h3>\ub9b4\ub808\uc774 \ucc44\ub110 \ub9e4\ud551</h3>"
            + "<table cellpadding='3' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>\ub9b4\ub808\uc774</th><th>\uc7a5\uce58</th><th>\uc6a9\ub3c4</th></tr>"
            + "<tr><td align='center'>0</td><td>-</td><td>\ubbf8\uc0ac\uc6a9</td></tr>"
            + "<tr><td align='center'>1</td><td style='color:#DC0000;'><b>\ube68\uac04\ub4f1</b></td><td>\ubc18\ucd9c\uc54c\ub9bc \uc790\ub3d9 \uc810\ub4f1 (5\ucd08)</td></tr>"
            + "<tr><td align='center'>2</td><td style='color:#1E96DC;'><b>\ubd80\uc800</b></td><td>\ubc18\ucd9c\uc54c\ub9bc \uc790\ub3d9 \uc6b8\ub9bc (5\ucd08)</td></tr>"
            + "</table>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>\ud0dc\uadf8 \ub370\uc774\ud130</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc2dc\uac04</b></td><td>\ud0dc\uadf8\uac00 \ub9c8\uc9c0\ub9c9\uc73c\ub85c \uc77d\ud78c \uc2dc\uac01 (yyyy-MM-dd HH:mm:ss)</td></tr>"
            + "<tr><td><b>\ub9ac\ub354\uae30</b></td><td>\ud0dc\uadf8\ub97c \uc77d\uc740 \ub9ac\ub354\uae30 \uc774\ub984</td></tr>"
            + "<tr><td><b>EPC</b></td><td>\ud0dc\uadf8\uc758 EPC(Electronic Product Code) 16\uc9c4\uc218 \uac12</td></tr>"
            + "<tr><td><b>RSSI</b></td><td>\uc218\uc2e0 \uc2e0\ud638 \uac15\ub3c4 (dBm), \uac12\uc774 \ud074\uc218\ub85d \uac00\uae4c\uc774 \uc704\uce58</td></tr>"
            + "<tr><td><b>\ud69f\uc218</b></td><td>\ub3d9\uc77c \ud0dc\uadf8\uac00 \uc77d\ud78c \ub204\uc801 \ud69f\uc218 (\uc911\ubcf5\uc81c\uac70 \ubaa8\ub4dc)</td></tr>"
            + "<tr><td style='background:#FFE8E8;color:#B40000;'><b>\ubc18\ucd9c\uc54c\ub9bc \ud589</b></td>"
            +     "<td>\ubbf8\ud5c8\uac00 \ubc18\ucd9c \uc790\uc0b0 (\uacbd\uad11\ub4f1 \ube68\uac04\ub4f1 + DB \uae30\ub85d)</td></tr>"
            + "<tr><td style='background:#E8F5E8;color:#006E00;'><b>\ubc18\ucd9c\ud5c8\uc6a9 \ud589</b></td>"
            +     "<td>\ubc18\ucd9c \ud5c8\uc6a9\ub41c \uc790\uc0b0 (\uc815\uc0c1 \ubc18\ucd9c)</td></tr>"
            + "</table>"

            + "<h3>\ud558\ub2e8 \uae30\ub2a5</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc911\ubcf5\uc81c\uac70</b></td><td>\uccb4\ud06c \uc2dc \ub3d9\uc77c EPC\ub97c \ud558\ub098\uc758 \ud589\uc73c\ub85c \ubcd1\ud569 (\ud69f\uc218 \uc99d\uac00)</td></tr>"
            + "<tr><td><b>\ucd08\uae30\ud654</b></td><td>\ud0dc\uadf8 \ub370\uc774\ud130 \uc804\uccb4 \uc0ad\uc81c</td></tr>"
            + "<tr><td><b>\uc5d1\uc140 \uc800\uc7a5</b></td><td>\ud604\uc7ac \ud0dc\uadf8 \ubaa9\ub85d\uc744 .xls \ud30c\uc77c\ub85c \ub0b4\ubcf4\ub0b4\uae30</td></tr>"
            + "<tr><td><b>DB \uc870\ud68c</b></td><td>\uae30\uac04\uc744 \uc9c0\uc815\ud558\uc5ec DB\uc5d0 \uc800\uc7a5\ub41c \ud0dc\uadf8 \uc774\ub825 \uc870\ud68c</td></tr>"
            + "</table>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>DB \ud14c\uc774\ube14\ubcc4 \ub370\uc774\ud130 \ud750\ub984</h2>"
            + "<table cellpadding='4' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'>"
            +   "<th>\ud14c\uc774\ube14</th><th>\uad00\ub9ac \uc8fc\uccb4</th><th>\ubbf8\ub4e4\uc6e8\uc5b4 \ub3d9\uc791</th><th>\uc2dc\uc810</th></tr>"
            + "<tr><td><b>assets</b><br>(\uc790\uc0b0 \ub9c8\uc2a4\ud130)</td>"
            +   "<td>\uc678\ubd80 \uc2dc\uc2a4\ud15c<br>+ API</td><td>SELECT \uc870\ud68c<br>INSERT/UPDATE</td>"
            +   "<td>\uc2dc\uc791 \uc2dc + 30\ucd08\ub9c8\ub2e4<br>\uba54\ubaa8\ub9ac \uce90\uc2dc \uac31\uc2e0</td></tr>"
            + "<tr><td><b>export_permissions</b><br>(\ubc18\ucd9c\ud5c8\uc6a9 \ubaa9\ub85d)</td>"
            +   "<td>\uc678\ubd80 \uc2dc\uc2a4\ud15c</td><td>SELECT \uc870\ud68c<br>(\uc720\ud6a8\uae30\uac04 \uccb4\ud06c)</td>"
            +   "<td>\uc2dc\uc791 \uc2dc + 30\ucd08\ub9c8\ub2e4<br>\uba54\ubaa8\ub9ac \uce90\uc2dc \uac31\uc2e0</td></tr>"
            + "<tr><td><b>export_alerts</b><br>(\ubc18\ucd9c\uc54c\ub9bc \uc774\ub825)</td>"
            +   "<td><b>\ubbf8\ub4e4\uc6e8\uc5b4</b></td><td><b>INSERT</b></td>"
            +   "<td>\ubbf8\ud5c8\uac00 \ubc18\ucd9c \uac10\uc9c0 \uc2dc<br>(30\ucd08 \uc911\ubcf5\uc81c\uac70)</td></tr>"
            + "<tr><td><b>tag_reads</b><br>(\ud0dc\uadf8 \uc77d\uae30 \uc774\ub825)</td>"
            +   "<td><b>\ubbf8\ub4e4\uc6e8\uc5b4</b></td><td><b>INSERT</b><br>(\ubc30\uce58 \ucc98\ub9ac)</td>"
            +   "<td>\ud0dc\uadf8 \uac10\uc9c0 \uc2dc<br>(\uce90\uc2dc MISS\ub9cc)</td></tr>"
            + "</table>"

            + "<h3>\ubbf8\ud5c8\uac00 \ubc18\ucd9c \ud310\ub2e8 \ud750\ub984</h3>"
            + "<p style='font-size:11px; line-height:1.6;'>"
            + "\ud0dc\uadf8 \uac10\uc9c0 &rarr; assets\uc5d0 EPC \uc874\uc7ac? &rarr; <b>Yes</b>: export_permissions\uc5d0 \uc720\ud6a8 \ud5c8\uc6a9 \uc788\uc74c? "
            + "&rarr; <b>No</b>: <span style='color:#B40000;'><b>\ubbf8\ud5c8\uac00 \ubc18\ucd9c!</b></span> "
            + "(\uacbd\uad11\ub4f1 ON 5\ucd08 + \ube68\uac04 \ud589 \ud45c\uc2dc + export_alerts INSERT + \ub85c\uadf8)<br>"
            + "&rarr; assets\uc5d0 \uc5c6\uac70\ub098 \ubc18\ucd9c \ud5c8\uc6a9\ub428 &rarr; \uc77c\ubc18 \ud0dc\uadf8 \ucc98\ub9ac"
            + "</p>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>\uc678\ubd80 API (REST)</h2>"
            + "<p>\ud3ec\ud2b8 <b>18080</b>\uc5d0\uc11c HTTP REST API\ub97c \uc81c\uacf5\ud569\ub2c8\ub2e4.</p>"

            + "<h3>\uc870\ud68c API (GET)</h3>"
            + "<table cellpadding='3' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>URL</th><th>\uc124\uba85</th></tr>"
            + "<tr><td><code>/api/readers</code></td><td>\ub9ac\ub354\uae30 \uc124\uc815\uc815\ubcf4 \uc804\uccb4 \uc870\ud68c</td></tr>"
            + "<tr><td><code>/api/assets</code></td><td>\uc790\uc0b0 \ud14c\uc774\ube14 \uc870\ud68c</td></tr>"
            + "<tr><td><code>/api/export-permissions</code></td><td>\ubc18\ucd9c\ud5c8\uc6a9 \ubaa9\ub85d \uc870\ud68c</td></tr>"
            + "<tr><td><code>/api/export-alerts?from=...&amp;to=...</code></td><td>\ubc18\ucd9c\uc54c\ub9bc \uc774\ub825 \uc870\ud68c (\uae30\uac04)</td></tr>"
            + "</table>"

            + "<h3>\uc81c\uc5b4 API (POST)</h3>"
            + "<table cellpadding='3' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>URL</th><th>\uc124\uba85</th></tr>"
            + "<tr><td><code>/api/control/connect-all</code></td><td>\uc804\uccb4 \ub9ac\ub354\uae30 \uc5f0\uacb0</td></tr>"
            + "<tr><td><code>/api/control/disconnect-all</code></td><td>\uc804\uccb4 \ub9ac\ub354\uae30 \uc5f0\uacb0 \ud574\uc81c</td></tr>"
            + "<tr><td><code>/api/control/start-inventory</code></td><td>\uc804\uccb4 \uc778\ubca4\ud1a0\ub9ac \uc2dc\uc791</td></tr>"
            + "<tr><td><code>/api/control/stop-inventory</code></td><td>\uc804\uccb4 \uc778\ubca4\ud1a0\ub9ac \uc911\uc9c0</td></tr>"
            + "</table>"

            + "<h3>\uc218\uc815 API</h3>"
            + "<table cellpadding='3' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>Method</th><th>URL</th><th>\uc124\uba85</th></tr>"
            + "<tr><td>PUT</td><td><code>/api/readers/{name}</code></td><td>\ub9ac\ub354\uae30 \uc124\uc815 \uc218\uc815</td></tr>"
            + "<tr><td>POST</td><td><code>/api/assets</code></td><td>\uc790\uc0b0 \ucd94\uac00</td></tr>"
            + "<tr><td>PUT</td><td><code>/api/assets/{id}</code></td><td>\uc790\uc0b0 \uc218\uc815</td></tr>"
            + "<tr><td>POST</td><td><code>/api/export-permissions</code></td><td>\ubc18\ucd9c\ud5c8\uc6a9 \ucd94\uac00</td></tr>"
            + "<tr><td>DELETE</td><td><code>/api/export-permissions/{id}</code></td><td>\ubc18\ucd9c\ud5c8\uc6a9 \uc0ad\uc81c</td></tr>"
            + "<tr><td>GET</td><td><code>/api/mask</code></td><td>EPC Mask \uc870\ud68c</td></tr>"
            + "<tr><td>PUT</td><td><code>/api/mask</code></td><td>EPC Mask \uc124\uc815</td></tr>"
            + "</table>"

            + "<h3>\uc751\ub2f5 \ud615\uc2dd (JSON)</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc131\uacf5</b></td><td><code>{\"status\":\"ok\",\"data\":[...]}</code></td></tr>"
            + "<tr><td><b>\uc2e4\ud328</b></td><td><code>{\"status\":\"error\",\"message\":\"...\"}</code></td></tr>"
            + "</table>"

            + "<h3>Swagger UI</h3>"
            + "<p><code>http://localhost:18080/swagger</code> &mdash; \ube0c\ub77c\uc6b0\uc800\uc5d0\uc11c API \ud14c\uc2a4\ud2b8 \uac00\ub2a5</p>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>EPC Mask \ud544\ud130</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>\uc124\uc815 \uc704\uce58</b></td><td>\uc124\uc815 \ub2e4\uc774\uc5bc\ub85c\uadf8 \uc0c1\ub2e8 EPC Mask \uc785\ub825 \ud544\ub4dc</td></tr>"
            + "<tr><td><b>\ub3d9\uc791</b></td><td>EPC\uac00 mask \uac12\uc73c\ub85c \uc2dc\uc791\ud558\ub294 \ud0dc\uadf8\ub9cc \ucc98\ub9ac (\ub300\uc18c\ubb38\uc790 \ubb34\uc2dc)</td></tr>"
            + "<tr><td><b>\ube48 \uac12</b></td><td>\ubaa8\ub4e0 \ud0dc\uadf8\ub97c \ucc98\ub9ac (\ud544\ud130 \ube44\ud65c\uc131)</td></tr>"
            + "<tr><td><b>\uc800\uc7a5 \ud30c\uc77c</b></td><td>readers.cfg\uc758 <code>MASK=</code> \ub77c\uc778</td></tr>"
            + "<tr><td><b>\uc801\uc6a9 \ubc94\uc704</b></td><td>\ubaa8\ub4e0 \ub9ac\ub354\uae30\uc5d0 \uacf5\ud1b5 \uc801\uc6a9 (\uae00\ub85c\ubc8c \uc124\uc815)</td></tr>"
            + "<tr><td><b>\uc7ac\uc2dc\uc791</b></td><td>\ubbf8\ub4e4\uc6e8\uc5b4 \uc7ac\uc2dc\uc791 \ubd88\ud544\uc694 (\uc124\uc815 \uc800\uc7a5 \uc989\uc2dc \uc801\uc6a9)</td></tr>"
            + "</table>"
            + "<p style='font-size:11px;'>\uc608: MASK=0420 \uc124\uc815 \uc2dc EPC\uac00 0420\uc73c\ub85c \uc2dc\uc791\ud558\ub294 \ud0dc\uadf8\ub9cc \ud45c\uc2dc/\uc800\uc7a5\ub429\ub2c8\ub2e4.</p>"

            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>\ub85c\uadf8</h2>"
            + "<p>\ub9ac\ub354\uae30 \uc5f0\uacb0, \uba85\ub839 \uc804\uc1a1, \uc624\ub958 \ub4f1 \ubbf8\ub4e4\uc6e8\uc5b4\uc758 \ubaa8\ub4e0 \ub3d9\uc791 \uc774\ub825\uc744 \ud45c\uc2dc\ud569\ub2c8\ub2e4.</p>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>\ud3ec\ub9f7</b></td><td><code>[\uc2dc\uac04] [\ub9ac\ub354\uae30\uba85] \uba54\uc2dc\uc9c0</code></td></tr>"
            + "<tr><td><b>\ucd5c\ub300</b></td><td>1,000\uc904 (\ucd08\uacfc \uc2dc \uc624\ub798\ub41c \ub85c\uadf8 \uc790\ub3d9 \uc0ad\uc81c)</td></tr>"
            + "<tr><td><b>\ub85c\uadf8 \uc800\uc7a5</b></td><td>\ud604\uc7ac \ub85c\uadf8\ub97c .txt \ud30c\uc77c\ub85c \uc800\uc7a5</td></tr>"
            + "<tr><td><b>\ub85c\uadf8 \uc9c0\uc6b0\uae30</b></td><td>\ud654\uba74\uc758 \ub85c\uadf8\ub97c \ubaa8\ub450 \uc0ad\uc81c</td></tr>"
            + "</table>"

            + "</body></html>";

        JEditorPane helpPane = new JEditorPane("text/html", html);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(helpPane);
        scrollPane.setPreferredSize(new Dimension(540, 600));

        JOptionPane.showMessageDialog(this, scrollPane, "\ub3c4\uc6c0\ub9d0", JOptionPane.PLAIN_MESSAGE);
    }

    private void showAssetDbDialog() {
        if (!DatabaseManager.getInstance().isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "\ub370\uc774\ud130\ubca0\uc774\uc2a4\uc5d0 \uc5f0\uacb0\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.",
                "\uc790\uc0b0 DB", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.BODY);

        tabbedPane.addTab("\uc790\uc0b0 \ubaa9\ub85d", createAssetsTab());
        tabbedPane.addTab("\ubc18\ucd9c\ud5c8\uc6a9 \ubaa9\ub85d", createPermissionsTab());
        tabbedPane.addTab("\ubc18\ucd9c\uc54c\ub9bc \uc774\ub825", createAlertsTab());
        tabbedPane.addTab("\uce90\uc2dc \uc0c1\ud0dc", createCacheTab());

        tabbedPane.setPreferredSize(new Dimension(750, 450));

        JOptionPane.showMessageDialog(this, tabbedPane, "\uc790\uc0b0 DB \uc870\ud68c", JOptionPane.PLAIN_MESSAGE);
    }

    private JPanel createAssetsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"\uc790\uc0b0\ubc88\ud638", "EPC", "\uc790\uc0b0\uba85", "\ubd80\uc11c", "\ub4f1\ub85d\uc77c\uc2dc", "\ubcf4\uc720\uc5ec\ubd80"};
        List<String[]> data = AssetRepository.getInstance().queryAssets();

        Object[][] rows = new Object[data.size()][cols.length];
        for (int i = 0; i < data.size(); i++) {
            String[] row = data.get(i);
            rows[i] = new String[] { row[0], row[1], row[2], row[3], row[4], row[5] };
        }

        JTable table = createStyledTable(rows, cols);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        JLabel countLabel = new JLabel("  \ucd1d " + data.size() + "\uac74");
        countLabel.setFont(Theme.SMALL);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createPermissionsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"EPC", "\uc790\uc0b0\ubc88\ud638", "\uc790\uc0b0\uba85", "\ud5c8\uc6a9\uc2dc\uc791", "\ud5c8\uc6a9\uc885\ub8cc", "\uc0ac\uc720", "\uc0c1\ud0dc"};
        List<String[]> data = AssetRepository.getInstance().queryExportPermissions();

        Object[][] rows = new Object[data.size()][cols.length];
        for (int i = 0; i < data.size(); i++) rows[i] = data.get(i);

        JTable table = createStyledTable(rows, cols);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(40);

        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if (!isSelected && value != null) {
                    if ("\uc720\ud6a8".equals(value.toString())) {
                        c.setForeground(new Color(0, 140, 0));
                    } else {
                        c.setForeground(new Color(180, 0, 0));
                    }
                }
                return c;
            }
        });

        JLabel countLabel = new JLabel("  \ucd1d " + data.size() + "\uac74");
        countLabel.setFont(Theme.SMALL);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createAlertsTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        JTextField fromField = new JTextField(today + " 00:00:00", 16);
        JTextField toField = new JTextField(sdf.format(new Date()), 16);
        fromField.setFont(Theme.SMALL);
        toField.setFont(Theme.SMALL);
        JButton queryBtn = Theme.createFlatButton("\uc870\ud68c", null);

        filterPanel.add(new JLabel("\uc2dc\uc791:"));
        filterPanel.add(fromField);
        filterPanel.add(new JLabel("  \uc885\ub8cc:"));
        filterPanel.add(toField);
        filterPanel.add(queryBtn);

        String[] cols = {"\uc54c\ub9bc\uc2dc\uac04", "\ub9ac\ub354\uae30", "EPC", "\uc790\uc0b0\ubc88\ud638", "\uc790\uc0b0\uba85", "RSSI"};
        JTable table = createStyledTable(new Object[0][cols.length], cols);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(40);

        JLabel countLabel = new JLabel("  \uc870\ud68c \ubc84\ud2bc\uc744 \ub204\ub974\uc138\uc694");
        countLabel.setFont(Theme.SMALL);

        queryBtn.addActionListener(e -> {
            List<String[]> data = AssetRepository.getInstance()
                .queryExportAlerts(fromField.getText().trim(), toField.getText().trim());
            Object[][] rows = new Object[data.size()][cols.length];
            for (int i = 0; i < data.size(); i++) rows[i] = data.get(i);
            table.setModel(new javax.swing.table.DefaultTableModel(rows, cols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
            Theme.styleTable(table);
            table.getColumnModel().getColumn(0).setPreferredWidth(130);
            table.getColumnModel().getColumn(1).setPreferredWidth(70);
            table.getColumnModel().getColumn(2).setPreferredWidth(200);
            table.getColumnModel().getColumn(3).setPreferredWidth(80);
            table.getColumnModel().getColumn(4).setPreferredWidth(100);
            table.getColumnModel().getColumn(5).setPreferredWidth(40);
            countLabel.setText("  \ucd1d " + data.size() + "\uac74");
        });

        queryBtn.doClick();

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createCacheTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel summaryLabel = new JLabel();
        summaryLabel.setFont(Theme.SMALL);

        JButton refreshBtn = Theme.createFlatButton("DB \uac31\uc2e0", null);
        refreshBtn.setToolTipText("AssetRepository.refreshCache() \uc989\uc2dc \uc2e4\ud589");

        JButton reloadBtn = Theme.createFlatButton("\ud654\uba74 \uc0c8\ub85c\uace0\uce68", null);

        topPanel.add(summaryLabel);
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(refreshBtn);
        topPanel.add(reloadBtn);

        JTabbedPane cacheTabs = new JTabbedPane(JTabbedPane.TOP);
        cacheTabs.setFont(Theme.SMALL);

        String[] assetCols = {"EPC (\uc815\uaddc\ud654)", "\uc790\uc0b0\ubc88\ud638", "\uc790\uc0b0\uba85", "\ubd80\uc11c"};
        JTable assetTable = createStyledTable(new Object[0][4], assetCols);

        String[] permitCols = {"EPC (\uc815\uaddc\ud654)"};
        JTable permitTable = createStyledTable(new Object[0][1], permitCols);

        String[] tagCacheCols = {"EPC"};
        JTable tagCacheTable = createStyledTable(new Object[0][1], tagCacheCols);

        String[] alertCacheCols = {"EPC"};
        JTable alertCacheTable = createStyledTable(new Object[0][1], alertCacheCols);

        cacheTabs.addTab("\uc790\uc0b0 \uce90\uc2dc", new JScrollPane(assetTable));
        cacheTabs.addTab("\ubc18\ucd9c\ud5c8\uc6a9 \uce90\uc2dc", new JScrollPane(permitTable));
        cacheTabs.addTab("\ud0dc\uadf8 DB\uce90\uc2dc (Caffeine)", new JScrollPane(tagCacheTable));
        cacheTabs.addTab("\uc54c\ub9bc \uc911\ubcf5\uc81c\uac70 (Caffeine)", new JScrollPane(alertCacheTable));

        Runnable loadCacheData = () -> {
            AssetRepository repo = AssetRepository.getInstance();

            Map<String, AssetRepository.AssetInfo> assetMap = repo.getAssetMapCopy();
            List<String[]> assetRows = new ArrayList<>();
            for (Map.Entry<String, AssetRepository.AssetInfo> entry : assetMap.entrySet()) {
                AssetRepository.AssetInfo info = entry.getValue();
                assetRows.add(new String[]{
                    entry.getKey(),
                    info.getAssetNumber() != null ? info.getAssetNumber() : "",
                    info.getAssetName() != null ? info.getAssetName() : "",
                    info.getDepartment() != null ? info.getDepartment() : ""
                });
            }
            Object[][] aRows = new Object[assetRows.size()][4];
            for (int i = 0; i < assetRows.size(); i++) aRows[i] = assetRows.get(i);
            assetTable.setModel(new javax.swing.table.DefaultTableModel(aRows, assetCols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });

            Set<String> permitted = repo.getPermittedEpcsCopy();
            Object[][] pRows = new Object[permitted.size()][1];
            int pi = 0;
            for (String epc : permitted) pRows[pi++] = new Object[]{epc};
            permitTable.setModel(new javax.swing.table.DefaultTableModel(pRows, permitCols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });

            Set<String> tagKeys = tagDataPanel.getDbDedupCacheKeys();
            Object[][] tRows = new Object[tagKeys.size()][1];
            int ti = 0;
            for (String epc : tagKeys) tRows[ti++] = new Object[]{epc};
            tagCacheTable.setModel(new javax.swing.table.DefaultTableModel(tRows, tagCacheCols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });

            Set<String> alertKeys = repo.getAlertDedupKeys();
            Object[][] alRows = new Object[alertKeys.size()][1];
            int ai = 0;
            for (String epc : alertKeys) alRows[ai++] = new Object[]{epc};
            alertCacheTable.setModel(new javax.swing.table.DefaultTableModel(alRows, alertCacheCols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });

            summaryLabel.setText(String.format(
                "\uc790\uc0b0: %d\uac74  |  \ubc18\ucd9c\ud5c8\uc6a9: %d\uac74  |  \ud0dc\uadf8 DB\uce90\uc2dc: %d\uac74  |  \uc54c\ub9bc\uce90\uc2dc: %d\uac74",
                assetMap.size(), permitted.size(),
                tagDataPanel.getDbDedupCacheSize(), repo.getAlertDedupSize()));
        };

        refreshBtn.addActionListener(e -> {
            AssetRepository.getInstance().refreshCache();
            loadCacheData.run();
            logPanel.appendLog("Cache manually refreshed");
        });
        reloadBtn.addActionListener(e -> loadCacheData.run());

        loadCacheData.run();

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(cacheTabs, BorderLayout.CENTER);
        return panel;
    }

    /** Styled table with zebra striping */
    private JTable createStyledTable(Object[][] rows, String[] cols) {
        JTable table = new JTable(rows, cols) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isCellSelected(row, column)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Theme.TABLE_STRIPE);
                }
                return c;
            }
        };
        Theme.styleTable(table);
        return table;
    }

    private void openFlowDiagram() {
        try {
            File htmlFile = new File("middleware-flow.html");
            if (!htmlFile.exists()) {
                JOptionPane.showMessageDialog(this,
                    "middleware-flow.html 파일을 찾을 수 없습니다.\n경로: " + htmlFile.getAbsolutePath(),
                    "파일 없음", JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
        } catch (Exception ex) {
            AppLogger.error("MainFrame", "흐름도 열기 실패", ex);
            JOptionPane.showMessageDialog(this,
                "브라우저를 열 수 없습니다: " + ex.getMessage(),
                "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openConfigDialog() {
        ConfigDialog dialog = new ConfigDialog(this, configs);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            configs = dialog.getConfigs();
            ReaderConfig.saveToFile(CONFIG_FILE, configs);
            logPanel.appendLog("Config saved: " + configs.size() + " reader(s)");
            initializeReaders();
        }
    }
}
