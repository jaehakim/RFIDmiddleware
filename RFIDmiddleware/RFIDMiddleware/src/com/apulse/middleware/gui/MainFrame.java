package com.apulse.middleware.gui;

import com.apulse.middleware.config.DatabaseConfig;
import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.db.AssetRepository;
import com.apulse.middleware.db.DatabaseManager;
import com.apulse.middleware.db.TagRepository;
import com.apulse.middleware.reader.ReaderConnection;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.reader.ReaderStatus;
import com.apulse.middleware.reader.WarningLightController;
import com.apulse.middleware.util.HexUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        AssetRepository.getInstance().start(30);

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
                    WarningLightController.getInstance().shutdown();
                    AssetRepository.getInstance().shutdown();
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
        JButton assetDbBtn = createToolButton("자산 DB", e -> showAssetDbDialog());
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
        toolBar.addSeparator(new Dimension(5, 0));
        toolBar.add(assetDbBtn);
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
            // 자산정보 및 상태 판단 (백그라운드 스레드에서 실행)
            AssetRepository.AssetInfo assetInfo = AssetRepository.getInstance().getAssetInfo(epc);
            AssetRepository.AssetInfo unauthorizedAsset = AssetRepository.getInstance().checkUnauthorizedExport(epc);

            // 상태 결정: 자산 아님 → null, 자산+미허가 → 반출알림, 자산+허용 → 반출허용
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
                    connection.getConfig().getName(), epc, rssi,
                    assetNumber, assetName, department, finalStatus);
                if (isNew) {
                    // 캐시 MISS → DB 저장
                    TagRepository.getInstance().insertTagRead(
                        epc, connection.getConfig().getName(),
                        rssi, HexUtils.nowShort());
                }

                // 미허가 반출 감지 → 경광등 + DB 기록 + 로그
                if (unauthorizedAsset != null && AssetRepository.getInstance().shouldAlert(epc)) {
                    String time = HexUtils.nowShort();
                    String readerName = connection.getConfig().getName();

                    // 경광등 트리거 (리더기 설정에서 경광등 '적용' 시에만)
                    if (connection.getConfig().isWarningLightEnabled()) {
                        WarningLightController.getInstance().triggerWarningLight(connection);
                    }

                    // DB 기록
                    AssetRepository.getInstance().insertAlert(
                        epc, unauthorizedAsset.getAssetNumber(), readerName, rssi, time);

                    // 로그
                    logPanel.appendLog(readerName,
                        "UNAUTHORIZED EXPORT: EPC=" + epc
                        + ", Asset=" + unauthorizedAsset.getAssetNumber()
                        + " (" + (unauthorizedAsset.getAssetName() != null ? unauthorizedAsset.getAssetName() : "") + ")");
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

            // --- 경광등 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>경광등 (반출알림)</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>동작 조건</b></td><td>반출알림 감지 + 리더기 설정에서 경광등 '적용'(1)</td></tr>"
            + "<tr><td><b>릴레이 채널</b></td><td>릴레이 2번 = <b style='color:#DC0000;'>빨간등</b></td></tr>"
            + "<tr><td><b>점등 시간</b></td><td><b>5초</b> 후 자동 소등</td></tr>"
            + "<tr><td><b>재감지 시</b></td><td>타이머 리셋 (5초 재시작)</td></tr>"
            + "<tr><td><b>알림 중복제거</b></td><td>같은 EPC 30초 내 재알림 방지</td></tr>"
            + "</table>"

            + "<h3>릴레이 채널 매핑</h3>"
            + "<table cellpadding='3' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'><th>릴레이</th><th>색상</th><th>용도</th></tr>"
            + "<tr><td align='center'>0</td><td>-</td><td>미사용</td></tr>"
            + "<tr><td align='center'>1</td><td style='color:#00A000;'><b>녹색</b></td><td>수동 제어 (우클릭 메뉴)</td></tr>"
            + "<tr><td align='center'>2</td><td style='color:#DC0000;'><b>빨간</b></td><td>반출알림 자동 점등 (5초)</td></tr>"
            + "</table>"

            // --- 태그 데이터 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>태그 데이터</h2>"
            + "<table cellpadding='4' cellspacing='0' border='0'>"
            + "<tr><td><b>시간</b></td><td>태그가 마지막으로 읽힌 시각 (yyyy-MM-dd HH:mm:ss)</td></tr>"
            + "<tr><td><b>리더기</b></td><td>태그를 읽은 리더기 이름</td></tr>"
            + "<tr><td><b>EPC</b></td><td>태그의 EPC(Electronic Product Code) 16진수 값</td></tr>"
            + "<tr><td><b>RSSI</b></td><td>수신 신호 강도 (dBm), 값이 클수록 가까이 위치</td></tr>"
            + "<tr><td><b>횟수</b></td><td>동일 태그가 읽힌 누적 횟수 (중복제거 모드)</td></tr>"
            + "<tr><td style='background:#FFD2D2;color:#B40000;'><b>반출알림 행</b></td>"
            +     "<td>미허가 반출 자산 (경광등 빨간등 + DB 기록)</td></tr>"
            + "<tr><td style='background:#D2F0D2;color:#006E00;'><b>반출허용 행</b></td>"
            +     "<td>반출 허용된 자산 (정상 반출)</td></tr>"
            + "</table>"

            + "<h3>하단 기능</h3>"
            + "<table cellpadding='3' cellspacing='0' border='0'>"
            + "<tr><td><b>중복제거</b></td><td>체크 시 동일 EPC를 하나의 행으로 병합 (횟수 증가)</td></tr>"
            + "<tr><td><b>초기화</b></td><td>태그 데이터 전체 삭제</td></tr>"
            + "<tr><td><b>엑셀 저장</b></td><td>현재 태그 목록을 .xls 파일로 내보내기</td></tr>"
            + "<tr><td><b>DB 조회</b></td><td>기간을 지정하여 DB에 저장된 태그 이력 조회</td></tr>"
            + "</table>"

            // --- DB 데이터 흐름 ---
            + "<h2 style='border-bottom:2px solid #336; padding-bottom:4px; margin-top:14px;'>DB 테이블별 데이터 흐름</h2>"
            + "<table cellpadding='4' cellspacing='0' border='1' style='border-collapse:collapse;'>"
            + "<tr style='background:#E8E8E8;'>"
            +   "<th>테이블</th><th>관리 주체</th><th>미들웨어 동작</th><th>시점</th></tr>"
            + "<tr><td><b>assets</b><br>(자산 마스터)</td>"
            +   "<td>외부 시스템</td><td>SELECT 조회</td>"
            +   "<td>시작 시 + 30초마다<br>메모리 캐시 갱신</td></tr>"
            + "<tr><td><b>export_permissions</b><br>(반출허용 목록)</td>"
            +   "<td>외부 시스템</td><td>SELECT 조회<br>(유효기간 체크)</td>"
            +   "<td>시작 시 + 30초마다<br>메모리 캐시 갱신</td></tr>"
            + "<tr><td><b>export_alerts</b><br>(반출알림 이력)</td>"
            +   "<td><b>미들웨어</b></td><td><b>INSERT</b></td>"
            +   "<td>미허가 반출 감지 시<br>(30초 중복제거)</td></tr>"
            + "<tr><td><b>tag_reads</b><br>(태그 읽기 이력)</td>"
            +   "<td><b>미들웨어</b></td><td><b>INSERT</b><br>(배치 처리)</td>"
            +   "<td>태그 감지 시<br>(캐시 MISS만)</td></tr>"
            + "</table>"

            + "<h3>미허가 반출 판단 흐름</h3>"
            + "<p style='font-size:11px; line-height:1.6;'>"
            + "태그 감지 &rarr; assets에 EPC 존재? &rarr; <b>Yes</b>: export_permissions에 유효 허용 있음? "
            + "&rarr; <b>No</b>: <span style='color:#B40000;'><b>미허가 반출!</b></span> "
            + "(경광등 ON 5초 + 빨간 행 표시 + export_alerts INSERT + 로그)<br>"
            + "&rarr; assets에 없거나 반출 허용됨 &rarr; 일반 태그 처리"
            + "</p>"

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
        scrollPane.setPreferredSize(new Dimension(540, 600));

        JOptionPane.showMessageDialog(this, scrollPane, "도움말", JOptionPane.PLAIN_MESSAGE);
    }

    /** 자산 DB 조회 다이얼로그 (3개 탭: 자산목록, 반출허용, 반출알림) */
    private void showAssetDbDialog() {
        if (!DatabaseManager.getInstance().isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "데이터베이스에 연결되어 있지 않습니다.",
                "자산 DB", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

        // --- 탭 1: 자산 목록 ---
        tabbedPane.addTab("자산 목록", createAssetsTab());

        // --- 탭 2: 반출허용 목록 ---
        tabbedPane.addTab("반출허용 목록", createPermissionsTab());

        // --- 탭 3: 반출알림 이력 ---
        tabbedPane.addTab("반출알림 이력", createAlertsTab());

        tabbedPane.setPreferredSize(new Dimension(750, 450));

        JOptionPane.showMessageDialog(this, tabbedPane, "자산 DB 조회", JOptionPane.PLAIN_MESSAGE);
    }

    /** 자산 목록 탭 생성 */
    private JPanel createAssetsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"자산번호", "EPC", "자산명", "부서", "등록일시"};
        List<String[]> data = AssetRepository.getInstance().queryAssets();

        Object[][] rows = new Object[data.size()][cols.length];
        for (int i = 0; i < data.size(); i++) rows[i] = data.get(i);

        JTable table = createStyledTable(rows, cols);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);

        JLabel countLabel = new JLabel("  총 " + data.size() + "건");
        countLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    /** 반출허용 목록 탭 생성 */
    private JPanel createPermissionsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"EPC", "자산번호", "자산명", "허용시작", "허용종료", "사유", "상태"};
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

        // 상태 컬럼 색상 렌더러
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if (!isSelected && value != null) {
                    if ("유효".equals(value.toString())) {
                        c.setForeground(new Color(0, 140, 0));
                    } else {
                        c.setForeground(new Color(180, 0, 0));
                    }
                }
                return c;
            }
        });

        JLabel countLabel = new JLabel("  총 " + data.size() + "건");
        countLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    /** 반출알림 이력 탭 생성 */
    private JPanel createAlertsTab() {
        JPanel panel = new JPanel(new BorderLayout());

        // 상단: 기간 선택
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        JTextField fromField = new JTextField(today + " 00:00:00", 16);
        JTextField toField = new JTextField(sdf.format(new Date()), 16);
        fromField.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        toField.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        JButton queryBtn = new JButton("조회");
        queryBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        filterPanel.add(new JLabel("시작:"));
        filterPanel.add(fromField);
        filterPanel.add(new JLabel("  종료:"));
        filterPanel.add(toField);
        filterPanel.add(queryBtn);

        // 테이블
        String[] cols = {"알림시간", "리더기", "EPC", "자산번호", "자산명", "RSSI"};
        JTable table = createStyledTable(new Object[0][cols.length], cols);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(40);

        JLabel countLabel = new JLabel("  조회 버튼을 누르세요");
        countLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        // 조회 버튼 동작
        queryBtn.addActionListener(e -> {
            List<String[]> data = AssetRepository.getInstance()
                .queryExportAlerts(fromField.getText().trim(), toField.getText().trim());
            Object[][] rows = new Object[data.size()][cols.length];
            for (int i = 0; i < data.size(); i++) rows[i] = data.get(i);
            table.setModel(new javax.swing.table.DefaultTableModel(rows, cols) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            });
            table.getColumnModel().getColumn(0).setPreferredWidth(130);
            table.getColumnModel().getColumn(1).setPreferredWidth(70);
            table.getColumnModel().getColumn(2).setPreferredWidth(200);
            table.getColumnModel().getColumn(3).setPreferredWidth(80);
            table.getColumnModel().getColumn(4).setPreferredWidth(100);
            table.getColumnModel().getColumn(5).setPreferredWidth(40);
            countLabel.setText("  총 " + data.size() + "건");
        });

        // 초기 조회 실행
        queryBtn.doClick();

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    /** 공통 스타일 JTable 생성 */
    private JTable createStyledTable(Object[][] rows, String[] cols) {
        JTable table = new JTable(rows, cols) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        return table;
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
