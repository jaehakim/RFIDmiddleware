package com.apulse.middleware.gui;

import com.apulse.middleware.config.ReaderConfig;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigDialog extends JDialog {
    private final ConfigTableModel tableModel;
    private final JTable table;
    private final JCheckBox applyAllCheck;
    private boolean confirmed = false;

    public ConfigDialog(Frame owner, List<ReaderConfig> configs) {
        super(owner, "리더기 설정", true);
        setSize(820, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(5, 5));

        // 테이블
        tableModel = new ConfigTableModel(configs);
        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 11));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // 컬럼 너비
        table.getColumnModel().getColumn(0).setPreferredWidth(80);   // 이름
        table.getColumnModel().getColumn(1).setPreferredWidth(120);  // IP
        table.getColumnModel().getColumn(2).setPreferredWidth(55);   // 포트
        table.getColumnModel().getColumn(3).setPreferredWidth(40);   // 부저
        table.getColumnModel().getColumn(4).setPreferredWidth(45);   // 출력1
        table.getColumnModel().getColumn(5).setPreferredWidth(45);   // 출력2
        table.getColumnModel().getColumn(6).setPreferredWidth(45);   // 출력3
        table.getColumnModel().getColumn(7).setPreferredWidth(45);   // 출력4
        table.getColumnModel().getColumn(8).setPreferredWidth(60);   // 드웰시간

        // 부저 컬럼: 체크박스 렌더러/에디터
        table.getColumnModel().getColumn(3).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(3).setCellEditor(table.getDefaultEditor(Boolean.class));

        // 숫자 컬럼 중앙 정렬
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 2; i <= 8; i++) {
            if (i != 3) { // 부저(체크박스) 제외
                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 우측 버튼
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton addButton = createSideButton("추가");
        addButton.addActionListener(e -> {
            int num = tableModel.getRowCount() + 1;
            tableModel.addRow(new ReaderConfig("Reader-" + String.format("%02d", num), "192.168.1.151", 20058));
        });

        JButton deleteButton = createSideButton("삭제");
        deleteButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) tableModel.removeRow(row);
        });

        JButton upButton = createSideButton("▲ 위로");
        upButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row > 0) {
                tableModel.moveRow(row, row - 1);
                table.setRowSelectionInterval(row - 1, row - 1);
            }
        });

        JButton downButton = createSideButton("▼ 아래로");
        downButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount() - 1) {
                tableModel.moveRow(row, row + 1);
                table.setRowSelectionInterval(row + 1, row + 1);
            }
        });

        sidePanel.add(addButton);
        sidePanel.add(Box.createVerticalStrut(5));
        sidePanel.add(deleteButton);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(upButton);
        sidePanel.add(Box.createVerticalStrut(5));
        sidePanel.add(downButton);

        add(sidePanel, BorderLayout.EAST);

        // 하단 저장/취소
        JPanel bottomPanel = new JPanel(new BorderLayout());

        applyAllCheck = new JCheckBox("전체적용 (1행 설정값을 전체 리더기에 적용)");
        applyAllCheck.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        bottomPanel.add(applyAllCheck, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton okButton = new JButton("저장");
        okButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        okButton.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            confirmed = true;
            setVisible(false);
        });

        JButton cancelButton = new JButton("취소");
        cancelButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        cancelButton.addActionListener(e -> setVisible(false));

        btnPanel.add(okButton);
        btnPanel.add(cancelButton);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createSideButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    public boolean isConfirmed() { return confirmed; }

    public List<ReaderConfig> getConfigs() {
        return tableModel.getConfigs();
    }

    private class ConfigTableModel extends AbstractTableModel {
        private final String[] columns = {"이름", "IP 주소", "포트", "부저", "출력1", "출력2", "출력3", "출력4", "드웰시간"};
        private final List<ReaderConfig> data;

        ConfigTableModel(List<ReaderConfig> configs) {
            this.data = new ArrayList<>();
            for (ReaderConfig c : configs) {
                ReaderConfig copy = new ReaderConfig(c.getName(), c.getIp(), c.getPort());
                copy.setBuzzerEnabled(c.isBuzzerEnabled());
                copy.setAntennaPowers(c.getAntennaPowers().clone());
                copy.setDwellTime(c.getDwellTime());
                data.add(copy);
            }
        }

        List<ReaderConfig> getConfigs() {
            return new ArrayList<>(data);
        }

        void addRow(ReaderConfig config) {
            data.add(config);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        void removeRow(int row) {
            data.remove(row);
            fireTableRowsDeleted(row, row);
        }

        void moveRow(int from, int to) {
            ReaderConfig item = data.remove(from);
            data.add(to, item);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return data.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public boolean isCellEditable(int row, int col) { return true; }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 3) return Boolean.class;
            if (col == 2 || (col >= 4 && col <= 8)) return Integer.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            ReaderConfig cfg = data.get(row);
            switch (col) {
                case 0: return cfg.getName();
                case 1: return cfg.getIp();
                case 2: return cfg.getPort();
                case 3: return cfg.isBuzzerEnabled();
                case 4: return cfg.getAntennaPowers()[0];
                case 5: return cfg.getAntennaPowers()[1];
                case 6: return cfg.getAntennaPowers()[2];
                case 7: return cfg.getAntennaPowers()[3];
                case 8: return cfg.getDwellTime();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            applySingle(data.get(row), value, col);
            fireTableCellUpdated(row, col);

            // 전체적용: 1행(row 0)의 설정 컬럼(3~8) 변경 시 나머지 행에 전파
            if (row == 0 && col >= 3 && applyAllCheck.isSelected()) {
                for (int r = 1; r < data.size(); r++) {
                    applySingle(data.get(r), value, col);
                    fireTableCellUpdated(r, col);
                }
            }
        }

        private void applySingle(ReaderConfig cfg, Object value, int col) {
            String str = value != null ? value.toString().trim() : "";
            switch (col) {
                case 0:
                    cfg.setName(str);
                    break;
                case 1:
                    cfg.setIp(str);
                    break;
                case 2:
                    try { cfg.setPort(Integer.parseInt(str)); } catch (NumberFormatException ignored) {}
                    break;
                case 3:
                    cfg.setBuzzerEnabled(Boolean.TRUE.equals(value));
                    break;
                case 4: case 5: case 6: case 7:
                    try {
                        int[] powers = cfg.getAntennaPowers();
                        powers[col - 4] = Integer.parseInt(str);
                        cfg.setAntennaPowers(powers);
                    } catch (NumberFormatException ignored) {}
                    break;
                case 8:
                    try { cfg.setDwellTime(Integer.parseInt(str)); } catch (NumberFormatException ignored) {}
                    break;
            }
        }
    }
}
