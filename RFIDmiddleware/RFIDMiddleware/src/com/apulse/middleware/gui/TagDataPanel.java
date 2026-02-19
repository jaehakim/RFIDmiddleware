package com.apulse.middleware.gui;

import com.apulse.middleware.reader.TagData;
import com.apulse.middleware.util.HexUtils;

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

public class TagDataPanel extends JPanel {
    private final TagTableModel tableModel;
    private final JTable table;
    private final JLabel countLabel;
    private final JCheckBox deduplicateCheck;

    /** 중복제거 모드: EPC+리더기 키로 병합 */
    private final Map<String, TagData> tagMap = new LinkedHashMap<>();
    /** 일반 모드: 모든 읽기를 개별 행으로 저장 */
    private final List<TagData> tagList = new ArrayList<>();

    public TagDataPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("태그 데이터"));

        tableModel = new TagTableModel();
        table = new JTable(tableModel);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // 컬럼 너비 설정
        table.getColumnModel().getColumn(0).setPreferredWidth(130);  // 시간 (yyyy-MM-dd HH:mm:ss)
        table.getColumnModel().getColumn(1).setPreferredWidth(60);   // 리더기
        table.getColumnModel().getColumn(2).setPreferredWidth(250);  // EPC
        table.getColumnModel().getColumn(3).setPreferredWidth(35);   // RSSI
        table.getColumnModel().getColumn(4).setPreferredWidth(35);   // 횟수

        // 중앙 정렬
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

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

        bottomPanel.add(countLabel);
        bottomPanel.add(Box.createHorizontalStrut(15));
        bottomPanel.add(deduplicateCheck);
        bottomPanel.add(Box.createHorizontalStrut(15));
        bottomPanel.add(clearButton);
        bottomPanel.add(Box.createHorizontalStrut(10));
        bottomPanel.add(exportButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /** 태그 데이터 추가/업데이트 */
    public void addTag(String readerName, String epc, int rssi) {
        String time = HexUtils.nowShort();

        // 중복제거 Map: EPC만으로 키 (리더기 무관하게 병합)
        TagData existing = tagMap.get(epc);
        if (existing != null) {
            existing.update(rssi, time, readerName);
        } else {
            tagMap.put(epc, new TagData(epc, readerName, rssi, time));
        }

        // 일반 List에도 항상 추가 (체크 전환 시 사용)
        tagList.add(new TagData(epc, readerName, rssi, time));

        tableModel.refresh();
        updateCountLabel();
    }

    /** 중복제거 체크박스 토글 시 테이블 새로고침 */
    private void onDeduplicateToggle() {
        tableModel.refresh();
        updateCountLabel();
    }

    private void updateCountLabel() {
        int count = isDeduplicateMode()
            ? tagMap.size()
            : tagList.size();
        countLabel.setText("Tags: " + count);
    }

    private boolean isDeduplicateMode() {
        return deduplicateCheck.isSelected();
    }

    /** 현재 모드에 따른 데이터 리스트 반환 */
    private List<TagData> getCurrentData() {
        if (isDeduplicateMode()) {
            return new ArrayList<>(tagMap.values());
        } else {
            return new ArrayList<>(tagList);
        }
    }

    /** 태그 데이터 초기화 */
    public void clearTags() {
        tagMap.clear();
        tagList.clear();
        tableModel.refresh();
        updateCountLabel();
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
        private final String[] columns = {"시간", "리더기", "EPC", "RSSI", "횟수"};
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
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 3 || col == 4) return Integer.class;
            return String.class;
        }
    }
}
