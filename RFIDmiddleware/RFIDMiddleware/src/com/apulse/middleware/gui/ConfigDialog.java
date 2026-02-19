package com.apulse.middleware.gui;

import com.apulse.middleware.config.ReaderConfig;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigDialog extends JDialog {
    private final ConfigTableModel tableModel;
    private final JTable table;
    private boolean confirmed = false;

    public ConfigDialog(Frame owner, List<ReaderConfig> configs) {
        super(owner, "리더기 설정", true);
        setSize(500, 350);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(5, 5));

        // 테이블
        tableModel = new ConfigTableModel(configs);
        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 좌측 버튼 패널
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton addButton = new JButton("추가");
        addButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        addButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addButton.addActionListener(e -> {
            int num = tableModel.getRowCount() + 1;
            tableModel.addRow(new ReaderConfig("Reader-" + String.format("%02d", num), "192.168.1.151", 20058));
        });

        JButton deleteButton = new JButton("삭제");
        deleteButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        });

        JButton upButton = new JButton("▲ 위로");
        upButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        upButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        upButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row > 0) {
                tableModel.moveRow(row, row - 1);
                table.setRowSelectionInterval(row - 1, row - 1);
            }
        });

        JButton downButton = new JButton("▼ 아래로");
        downButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        downButton.setAlignmentX(Component.CENTER_ALIGNMENT);
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

        // 하단 확인/취소
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("저장");
        okButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        okButton.addActionListener(e -> {
            // 편집 중인 셀 커밋
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            confirmed = true;
            setVisible(false);
        });

        JButton cancelButton = new JButton("취소");
        cancelButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        cancelButton.addActionListener(e -> setVisible(false));

        bottomPanel.add(okButton);
        bottomPanel.add(cancelButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() { return confirmed; }

    public List<ReaderConfig> getConfigs() {
        return tableModel.getConfigs();
    }

    private static class ConfigTableModel extends AbstractTableModel {
        private final String[] columns = {"이름", "IP 주소", "포트"};
        private final List<ReaderConfig> data;

        ConfigTableModel(List<ReaderConfig> configs) {
            this.data = new ArrayList<>();
            for (ReaderConfig c : configs) {
                data.add(new ReaderConfig(c.getName(), c.getIp(), c.getPort()));
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
        public Object getValueAt(int row, int col) {
            ReaderConfig cfg = data.get(row);
            switch (col) {
                case 0: return cfg.getName();
                case 1: return cfg.getIp();
                case 2: return cfg.getPort();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            ReaderConfig cfg = data.get(row);
            String str = value.toString().trim();
            switch (col) {
                case 0:
                    cfg.setName(str);
                    break;
                case 1:
                    cfg.setIp(str);
                    break;
                case 2:
                    try {
                        cfg.setPort(Integer.parseInt(str));
                    } catch (NumberFormatException ignored) {}
                    break;
            }
            fireTableCellUpdated(row, col);
        }
    }
}
