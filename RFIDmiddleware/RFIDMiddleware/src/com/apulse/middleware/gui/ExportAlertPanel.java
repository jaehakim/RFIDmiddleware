package com.apulse.middleware.gui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExportAlertPanel extends JPanel {
    private final AlertTableModel tableModel;
    private final JTable table;
    private final JLabel countLabel;
    private final JLabel blinkIndicator;
    private final Timer blinkTimer;
    private boolean blinkState = false;
    private int alertCount = 0;

    public ExportAlertPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("반출 알림"));

        tableModel = new AlertTableModel();
        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // 컬럼 너비 설정
        table.getColumnModel().getColumn(0).setPreferredWidth(130);  // 시간
        table.getColumnModel().getColumn(1).setPreferredWidth(60);   // 리더기
        table.getColumnModel().getColumn(2).setPreferredWidth(200);  // EPC
        table.getColumnModel().getColumn(3).setPreferredWidth(80);   // 자산번호
        table.getColumnModel().getColumn(4).setPreferredWidth(80);   // 자산명
        table.getColumnModel().getColumn(5).setPreferredWidth(40);   // RSSI

        // 빨간 배경 행 렌더러
        AlertRowRenderer alertRenderer = new AlertRowRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(alertRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 하단 상태바
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        blinkIndicator = new JLabel(" ");
        blinkIndicator.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        blinkIndicator.setForeground(Color.RED);
        blinkIndicator.setPreferredSize(new Dimension(20, 20));

        countLabel = new JLabel("알림: 0건");
        countLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

        JButton clearButton = new JButton("알림 초기화");
        clearButton.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        clearButton.addActionListener(e -> clearAlerts());

        bottomPanel.add(blinkIndicator);
        bottomPanel.add(countLabel);
        bottomPanel.add(Box.createHorizontalStrut(15));
        bottomPanel.add(clearButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // 깜빡임 타이머 (500ms)
        blinkTimer = new Timer(500, e -> {
            if (alertCount > 0) {
                blinkState = !blinkState;
                blinkIndicator.setText(blinkState ? "!" : " ");
            } else {
                blinkIndicator.setText(" ");
            }
        });
        blinkTimer.start();
    }

    /** 반출알림 추가 (EDT에서 호출) */
    public void addAlert(String time, String readerName, String epc, String assetNumber, String assetName, int rssi) {
        tableModel.addRow(new AlertRow(time, readerName, epc, assetNumber, assetName, rssi));
        alertCount++;
        countLabel.setText("알림: " + alertCount + "건");
    }

    /** 알림 초기화 */
    public void clearAlerts() {
        tableModel.clear();
        alertCount = 0;
        countLabel.setText("알림: 0건");
        blinkState = false;
        blinkIndicator.setText(" ");
    }

    public int getAlertCount() {
        return alertCount;
    }

    /** 알림 행 데이터 */
    private static class AlertRow {
        final String time;
        final String readerName;
        final String epc;
        final String assetNumber;
        final String assetName;
        final int rssi;

        AlertRow(String time, String readerName, String epc, String assetNumber, String assetName, int rssi) {
            this.time = time;
            this.readerName = readerName;
            this.epc = epc;
            this.assetNumber = assetNumber;
            this.assetName = assetName;
            this.rssi = rssi;
        }
    }

    /** 테이블 모델 */
    private class AlertTableModel extends AbstractTableModel {
        private final String[] columns = {"시간", "리더기", "EPC", "자산번호", "자산명", "RSSI"};
        private final List<AlertRow> data = new ArrayList<>();

        void addRow(AlertRow row) {
            data.add(0, row);  // 최신 알림을 맨 위에
            fireTableRowsInserted(0, 0);
        }

        void clear() {
            data.clear();
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
            AlertRow r = data.get(row);
            switch (col) {
                case 0: return r.time;
                case 1: return r.readerName;
                case 2: return r.epc;
                case 3: return r.assetNumber;
                case 4: return r.assetName;
                case 5: return r.rssi;
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 5) return Integer.class;
            return String.class;
        }
    }

    /** 빨간 배경 행 렌더러 */
    private static class AlertRowRenderer extends DefaultTableCellRenderer {
        private static final Color ALERT_BG = new Color(255, 220, 220);
        private static final Color ALERT_FG = new Color(180, 0, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(ALERT_BG);
                c.setForeground(ALERT_FG);
            }
            return c;
        }
    }
}
