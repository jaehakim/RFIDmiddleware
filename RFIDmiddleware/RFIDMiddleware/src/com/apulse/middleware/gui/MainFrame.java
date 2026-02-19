package com.apulse.middleware.gui;

import com.apulse.middleware.config.DatabaseConfig;
import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.db.DatabaseManager;
import com.apulse.middleware.db.TagRepository;
import com.apulse.middleware.reader.ReaderConnection;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.reader.ReaderStatus;
import com.apulse.middleware.util.HexUtils;

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

        // DB/캐시 초기화
        DatabaseConfig dbConfig = new DatabaseConfig();
        DatabaseManager.getInstance().initialize(dbConfig);
        TagRepository.getInstance().start();

        readerManager = new ReaderManager();
        statusPanel = new ReaderStatusPanel();
        tagDataPanel = new TagDataPanel(dbConfig.getCacheTtlSeconds(), dbConfig.getCacheMaxSize());
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
                    TagRepository.getInstance().shutdown();
                    DatabaseManager.getInstance().shutdown();
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
        JButton helpBtn = createToolButton("도움말", e -> showHelpDialog());

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
        toolBar.add(helpBtn);
        toolBar.addSeparator(new Dimension(5, 0));
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
                    // FETCH된 안테나 설정을 readers.cfg에 영구 반영
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
                        saveConfig();
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
            SwingUtilities.invokeLater(() -> {
                boolean isNew = tagDataPanel.addTag(
                    connection.getConfig().getName(), epc, rssi);
                if (isNew) {
                    // 캐시 MISS → DB 저장
                    TagRepository.getInstance().insertTagRead(
                        epc, connection.getConfig().getName(),
                        rssi, HexUtils.nowShort());
                }
            });
        };

        readerManager.initialize(configs, statusListener, tagListener);
        statusPanel.initialize(configs, readerManager, this::saveConfig);
    }

    /** ReaderConnection의 인덱스 찾기 */
    private int findConnectionIndex(ReaderConnection connection) {
        List<ReaderConnection> connections = readerManager.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i) == connection) return i;
        }
        return -1;
    }

    /** 현재 설정을 파일에 저장 */
    private void saveConfig() {
        ReaderConfig.saveToFile(CONFIG_FILE, configs);
    }

    /** 도움말 다이얼로그 */
    private void showHelpDialog() {
        String html = "<html><body style='font-family:맑은 고딕,sans-serif; width:450px; padding:4px;'>"

            // --- 리더기 상태 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px;'>리더기 상태</h2>"

            + "<h3>상태 색상</h3>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b style='color:#B4B4B4;'>&#9632;</b></td>"
            +     "<td><b>미연결</b></td><td>리더기와 연결되지 않은 초기 상태</td></tr>"
            + "<tr><td><b style='color:#FFC800;'>&#9632;</b></td>"
            +     "<td><b>연결 중</b></td><td>리더기에 TCP 연결을 시도하는 중</td></tr>"
            + "<tr><td><b style='color:#00B400;'>&#9632;</b></td>"
            +     "<td><b>연결됨</b></td><td>리더기와 정상 연결된 상태 (안테나 설정 자동 수신 완료)</td></tr>"
            + "<tr><td><b style='color:#0078FF;'>&#9632;</b></td>"
            +     "<td><b>읽기 중</b></td><td>인벤토리 실행 중 (태그 읽기 동작, 점멸)</td></tr>"
            + "<tr><td><b style='color:#DC0000;'>&#9632;</b></td>"
            +     "<td><b>오류</b></td><td>연결 실패 또는 통신 오류 발생</td></tr>"
            + "</table>"

            + "<h3>아이콘 표시</h3>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td>좌측 색상 바</td><td>현재 리더기 상태를 색상으로 표시</td></tr>"
            + "<tr><td><b style='color:#1E96DC;'>&#9679;</b> B</td>"
            +     "<td><b>부저</b> &mdash; 파란색: ON / 회색: OFF</td></tr>"
            + "<tr><td><b style='color:#FFB400;'>&#9679;</b> L</td>"
            +     "<td><b>경광등</b> &mdash; 주황색: ON / 회색: OFF</td></tr>"
            + "</table>"

            + "<h3>우클릭 메뉴</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>연결 / 연결 해제</b></td><td>개별 리더기 TCP 연결 제어</td></tr>"
            + "<tr><td><b>인벤토리 시작 / 중지</b></td><td>태그 읽기 동작 제어</td></tr>"
            + "<tr><td><b>경광등 ON / OFF</b></td><td>리더기 릴레이(경광등) 제어</td></tr>"
            + "<tr><td><b>부저 ON / OFF</b></td><td>리더기 부저 제어</td></tr>"
            + "<tr><td><b>안테나 설정</b></td><td>안테나 출력(dBm) 및 드웰시간(ms) 설정</td></tr>"
            + "</table>"

            + "<h3>연결 시 설정 동작</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>안테나/드웰</b></td><td>리더기의 실제 설정값을 자동으로 가져와 표시 (FETCH)</td></tr>"
            + "<tr><td><b>부저</b></td><td>저장된 설정값을 리더기에 적용 (PUSH)</td></tr>"
            + "<tr><td><b>설정 저장</b></td><td>가져온 설정값이 readers.cfg에 자동 저장됨</td></tr>"
            + "</table>"

            // --- 태그 데이터 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>태그 데이터</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>시간</b></td><td>태그가 마지막으로 읽힌 시각 (yyyy-MM-dd HH:mm:ss)</td></tr>"
            + "<tr><td><b>리더기</b></td><td>태그를 읽은 리더기 이름</td></tr>"
            + "<tr><td><b>EPC</b></td><td>태그의 EPC(Electronic Product Code) 16진수 값</td></tr>"
            + "<tr><td><b>RSSI</b></td><td>수신 신호 강도 (dBm), 값이 클수록 가까이 위치</td></tr>"
            + "<tr><td><b>횟수</b></td><td>동일 태그가 읽힌 누적 횟수 (중복제거 모드)</td></tr>"
            + "</table>"

            + "<h3>하단 기능</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>중복제거</b></td><td>체크 시 동일 EPC를 하나의 행으로 병합 (횟수 증가)</td></tr>"
            + "<tr><td><b>초기화</b></td><td>태그 데이터 전체 삭제</td></tr>"
            + "<tr><td><b>엑셀 저장</b></td><td>현재 태그 목록을 .xls 파일로 내보내기</td></tr>"
            + "<tr><td><b>DB 조회</b></td><td>기간을 지정하여 DB에 저장된 태그 이력 조회</td></tr>"
            + "</table>"

            // --- 로그 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>로그</h2>"
            + "<p>리더기 연결, 명령 전송, 오류 등 미들웨어의 모든 동작 이력을 표시합니다.</p>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>포맷</b></td><td><code>[시간] [리더기명] 메시지</code></td></tr>"
            + "<tr><td><b>최대</b></td><td>1,000줄 (초과 시 오래된 로그 자동 삭제)</td></tr>"
            + "<tr><td><b>로그 저장</b></td><td>현재 로그를 .txt 파일로 저장</td></tr>"
            + "<tr><td><b>로그 지우기</b></td><td>화면의 로그를 모두 삭제</td></tr>"
            + "</table>"

            + "</body></html>";

        JEditorPane helpPane = new JEditorPane("text/html", html);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(helpPane);
        scrollPane.setPreferredSize(new Dimension(520, 520));

        JOptionPane.showMessageDialog(this, scrollPane, "도움말", JOptionPane.PLAIN_MESSAGE);
    }

    /** 설정 다이얼로그 열기 */
    private void openConfigDialog() {
        ConfigDialog dialog = new ConfigDialog(this, configs);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            configs = dialog.getConfigs();
            ReaderConfig.saveToFile(CONFIG_FILE, configs);
            logPanel.appendLog("Config saved: " + configs.size() + " reader(s)");

            // 재초기화
            initializeReaders();
        }
    }
}
