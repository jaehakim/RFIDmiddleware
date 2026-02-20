package com.apulse.middleware.gui;

import com.apulse.middleware.db.DatabaseManager;
import com.apulse.middleware.db.TagRepository;
import com.apulse.middleware.reader.TagData;
import com.apulse.middleware.util.HexUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TagDataPanel extends JPanel {
    private final TagTableModel tableModel;
    private final JTable table;
    private final JLabel countLabel;
    private final JCheckBox deduplicateCheck;

    /** Caffeine 캐시: DB 저장 여부 판단용 (TTL 기반, 화면 표시에는 사용 안 함) */
    private Cache<String, Boolean> dbDedupCache;
    /** 중복제거 모드: EPC별 최신 데이터 (만료 없음, 초기화 시까지 유지) */
    private final Map<String, TagData> tagDedup = new LinkedHashMap<>();
    /** 일반 모드: 모든 읽기를 개별 행으로 저장 */
    private final List<TagData> tagList = new ArrayList<>();

    public static final String STATUS_PERMITTED = "반출허용";
    public static final String STATUS_ALERT = "반출알림";

    public TagDataPanel() {
        this(30, 10000);
    }

    public TagDataPanel(int cacheTtlSeconds, int cacheMaxSize) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("태그 데이터"));

        dbDedupCache = Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(cacheMaxSize)
            .build();

        tableModel = new TagTableModel();
        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // 컬럼 너비 설정
        table.getColumnModel().getColumn(0).setPreferredWidth(130);  // 시간
        table.getColumnModel().getColumn(1).setPreferredWidth(60);   // 리더기
        table.getColumnModel().getColumn(2).setPreferredWidth(200);  // EPC
        table.getColumnModel().getColumn(3).setPreferredWidth(35);   // RSSI
        table.getColumnModel().getColumn(4).setPreferredWidth(35);   // 횟수
        table.getColumnModel().getColumn(5).setPreferredWidth(80);   // 자산번호
        table.getColumnModel().getColumn(6).setPreferredWidth(100);  // 자산명
        table.getColumnModel().getColumn(7).setPreferredWidth(70);   // 부서
        table.getColumnModel().getColumn(8).setPreferredWidth(65);   // 상태

        // 상태별 배경색 렌더러
        StatusAwareRenderer renderer = new StatusAwareRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 하단 상태바
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        countLabel = new JLabel("Tags: 0");
        countLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        deduplicateCheck = new JCheckBox("중복제거", true);
        deduplicateCheck.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        deduplicateCheck.addActionListener(e -> onDeduplicateToggle());

        JButton clearButton = new JButton("초기화");
        clearButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        clearButton.addActionListener(e -> clearTags());

        JButton exportButton = new JButton("엑셀 저장");
        exportButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        exportButton.addActionListener(e -> exportToExcel());

        JButton dbQueryButton = new JButton("DB 조회");
        dbQueryButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        dbQueryButton.addActionListener(e -> showDbQueryDialog());

        bottomPanel.add(countLabel);
        bottomPanel.add(Box.createHorizontalStrut(15));
        bottomPanel.add(deduplicateCheck);
        bottomPanel.add(Box.createHorizontalStrut(15));
        bottomPanel.add(clearButton);
        bottomPanel.add(Box.createHorizontalStrut(10));
        bottomPanel.add(exportButton);
        bottomPanel.add(Box.createHorizontalStrut(10));
        bottomPanel.add(dbQueryButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * 태그 데이터 추가/업데이트
     * @param assetStatus 상태 (null=일반태그, STATUS_PERMITTED="반출허용", STATUS_ALERT="반출알림")
     * @return true = 캐시 MISS (새 태그 또는 TTL 만료 후 재읽기) → DB 저장 필요
     *         false = 캐시 HIT (중복) → DB 저장 불필요
     */
    public boolean addTag(String readerName, String epc, int rssi,
                          String assetNumber, String assetName, String department,
                          String assetStatus) {
        String time = HexUtils.nowShort();

        // DB 저장 판단: Caffeine TTL 캐시 (화면 표시와 무관)
        boolean isNew = (dbDedupCache.getIfPresent(epc) == null);
        if (isNew) {
            dbDedupCache.put(epc, Boolean.TRUE);
        }

        // 중복제거 표시용 Map (만료 없음, 초기화 시까지 유지)
        TagData existing = tagDedup.get(epc);
        if (existing != null) {
            existing.update(rssi, time, readerName);
            existing.setAssetInfo(assetNumber, assetName, department, assetStatus);
        } else {
            TagData newTag = new TagData(epc, readerName, rssi, time);
            newTag.setAssetInfo(assetNumber, assetName, department, assetStatus);
            tagDedup.put(epc, newTag);
        }

        // 일반 List에도 항상 추가 (비중복제거 모드용)
        TagData listTag = new TagData(epc, readerName, rssi, time);
        listTag.setAssetInfo(assetNumber, assetName, department, assetStatus);
        tagList.add(listTag);

        tableModel.refresh();
        updateCountLabel();
        return isNew;
    }

    /** 중복제거 체크박스 토글 시 테이블 새로고침 */
    private void onDeduplicateToggle() {
        tableModel.refresh();
        updateCountLabel();
    }

    private void updateCountLabel() {
        int count = isDeduplicateMode()
            ? tagDedup.size()
            : tagList.size();
        countLabel.setText("Tags: " + count);
    }

    private boolean isDeduplicateMode() {
        return deduplicateCheck.isSelected();
    }

    /** 현재 모드에 따른 데이터 리스트 반환 */
    private List<TagData> getCurrentData() {
        if (isDeduplicateMode()) {
            return new ArrayList<>(tagDedup.values());
        } else {
            return new ArrayList<>(tagList);
        }
    }

    /** 태그 데이터 초기화 */
    public void clearTags() {
        dbDedupCache.invalidateAll();
        tagDedup.clear();
        tagList.clear();
        tableModel.refresh();
        updateCountLabel();
    }

    /** DB 조회 다이얼로그 */
    private void showDbQueryDialog() {
        if (!DatabaseManager.getInstance().isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "데이터베이스에 연결되어 있지 않습니다.",
                "DB 조회", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 기본값: 오늘 00:00:00 ~ 현재 시각
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        JTextField fromField = new JTextField(today + " 00:00:00");
        JTextField toField = new JTextField(sdf.format(new Date()));

        panel.add(new JLabel("시작 시간:"));
        panel.add(fromField);
        panel.add(new JLabel("종료 시간:"));
        panel.add(toField);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "DB 조회 - 기간 선택", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        List<TagData> dbData = TagRepository.getInstance()
            .getTagReads(fromField.getText().trim(), toField.getText().trim());

        if (dbData.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "조회된 데이터가 없습니다.",
                "DB 조회", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 결과를 새 다이얼로그 테이블로 표시
        String[] cols = {"시간", "리더기", "EPC", "RSSI"};
        Object[][] rows = new Object[dbData.size()][4];
        for (int i = 0; i < dbData.size(); i++) {
            TagData t = dbData.get(i);
            rows[i][0] = t.getLastSeen();
            rows[i][1] = t.getReaderName();
            rows[i][2] = t.getEpc();
            rows[i][3] = t.getRssi();
        }

        JTable resultTable = new JTable(rows, cols);
        resultTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        resultTable.setRowHeight(20);
        resultTable.setAutoCreateRowSorter(true);

        JScrollPane sp = new JScrollPane(resultTable);
        sp.setPreferredSize(new Dimension(600, 400));

        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(this), sp,
            "DB 조회 결과 (" + dbData.size() + "건)", JOptionPane.PLAIN_MESSAGE);
    }

    /** 태그 데이터를 엑셀(xls) 파일로 저장 */
    private void exportToExcel() {
        List<TagData> data = getCurrentData();
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No tag data to export.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String defaultName = "tags_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xls";
        JFileChooser chooser = new JFileChooser(".");
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel (*.xls)", "xls"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xls")) {
            file = new File(file.getAbsolutePath() + ".xls");
        }

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            pw.println("<html>");
            pw.println("<head><meta charset=\"UTF-8\">");
            pw.println("<style>td,th { mso-number-format:\\@; }</style>");
            pw.println("</head>");
            pw.println("<body>");
            pw.println("<table border=\"1\">");

            pw.println("<tr>");
            for (String col : tableModel.columns) {
                pw.println("  <th>" + col + "</th>");
            }
            pw.println("</tr>");

            for (TagData tag : data) {
                pw.println("<tr>");
                pw.println("  <td>" + tag.getLastSeen() + "</td>");
                pw.println("  <td>" + tag.getReaderName() + "</td>");
                pw.println("  <td>" + tag.getEpc() + "</td>");
                pw.println("  <td>" + tag.getRssi() + "</td>");
                pw.println("  <td>" + tag.getCount() + "</td>");
                pw.println("  <td>" + (tag.getAssetNumber() != null ? tag.getAssetNumber() : "") + "</td>");
                pw.println("  <td>" + (tag.getAssetName() != null ? tag.getAssetName() : "") + "</td>");
                pw.println("  <td>" + (tag.getDepartment() != null ? tag.getDepartment() : "") + "</td>");
                pw.println("  <td>" + (tag.getAssetStatus() != null ? tag.getAssetStatus() : "") + "</td>");
                pw.println("</tr>");
            }

            pw.println("</table>");
            pw.println("</body></html>");

            JOptionPane.showMessageDialog(this,
                "Saved: " + file.getAbsolutePath() + "\n(" + data.size() + " rows)",
                "Excel Export", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Export failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private class TagTableModel extends AbstractTableModel {
        private final String[] columns = {"시간", "리더기", "EPC", "RSSI", "횟수", "자산번호", "자산명", "부서", "상태"};
        private List<TagData> data = new ArrayList<>();

        void refresh() {
            data = getCurrentData();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return data.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            TagData tag = data.get(row);
            switch (col) {
                case 0: return tag.getLastSeen();
                case 1: return tag.getReaderName();
                case 2: return tag.getEpc();
                case 3: return tag.getRssi();
                case 4: return tag.getCount();
                case 5: return tag.getAssetNumber() != null ? tag.getAssetNumber() : "";
                case 6: return tag.getAssetName() != null ? tag.getAssetName() : "";
                case 7: return tag.getDepartment() != null ? tag.getDepartment() : "";
                case 8: return tag.getAssetStatus() != null ? tag.getAssetStatus() : "";
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 3 || col == 4) return Integer.class;
            return String.class;
        }

        /** 해당 행의 상태 반환 */
        String getStatusAt(int row) {
            if (row >= 0 && row < data.size()) {
                return data.get(row).getAssetStatus();
            }
            return null;
        }
    }

    /** 상태별 배경색: 반출알림=빨강, 반출허용=초록, 일반=흰색 */
    private class StatusAwareRenderer extends DefaultTableCellRenderer {
        private static final Color ALERT_BG = new Color(255, 210, 210);
        private static final Color ALERT_FG = new Color(180, 0, 0);
        private static final Color PERMIT_BG = new Color(210, 240, 210);
        private static final Color PERMIT_FG = new Color(0, 110, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                String status = tableModel.getStatusAt(modelRow);
                if (STATUS_ALERT.equals(status)) {
                    c.setBackground(ALERT_BG);
                    c.setForeground(ALERT_FG);
                } else if (STATUS_PERMITTED.equals(status)) {
                    c.setBackground(PERMIT_BG);
                    c.setForeground(PERMIT_FG);
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
            }

            // 중앙 정렬 (시간, RSSI, 횟수, 자산번호, 부서, 상태)
            if (column == 0 || column == 3 || column == 4 || column == 5 || column == 7 || column == 8) {
                setHorizontalAlignment(JLabel.CENTER);
            } else {
                setHorizontalAlignment(JLabel.LEFT);
            }

            return c;
        }
    }
}
