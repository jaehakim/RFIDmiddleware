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
    private final JTextField maskField;
    private boolean confirmed = false;

    public ConfigDialog(Frame owner, List<ReaderConfig> configs) {
        super(owner, "\ub9ac\ub354\uae30 \uc124\uc815", true);
        setSize(920, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(5, 5));

        // Table
        tableModel = new ConfigTableModel(configs);
        table = new JTable(tableModel);
        Theme.styleTable(table);
        table.setRowHeight(24);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(55);
        table.getColumnModel().getColumn(3).setPreferredWidth(40);
        table.getColumnModel().getColumn(4).setPreferredWidth(50);
        table.getColumnModel().getColumn(5).setPreferredWidth(45);
        table.getColumnModel().getColumn(6).setPreferredWidth(45);
        table.getColumnModel().getColumn(7).setPreferredWidth(45);
        table.getColumnModel().getColumn(8).setPreferredWidth(45);
        table.getColumnModel().getColumn(9).setPreferredWidth(60);
        table.getColumnModel().getColumn(10).setPreferredWidth(50);

        // Boolean columns: checkbox renderer/editor
        table.getColumnModel().getColumn(3).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(3).setCellEditor(table.getDefaultEditor(Boolean.class));
        table.getColumnModel().getColumn(4).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(4).setCellEditor(table.getDefaultEditor(Boolean.class));
        table.getColumnModel().getColumn(10).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(10).setCellEditor(table.getDefaultEditor(Boolean.class));

        // Center-align number columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 2; i <= 9; i++) {
            if (i != 3 && i != 4) {
                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        // EPC Mask panel
        JPanel maskPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        maskPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JLabel maskLabel = new JLabel("EPC Mask:");
        maskLabel.setFont(Theme.SECTION_LABEL);
        maskField = new JTextField(ReaderConfig.getEpcMask(), 20);
        maskField.setFont(Theme.BODY);
        JLabel maskHint = new JLabel("(EPC \uc811\ub450\uc0ac \ud544\ud130, \ube48 \uac12\uc774\uba74 \uc804\uccb4 \uc218\uc2e0)");
        maskHint.setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.PLAIN, 10));
        maskHint.setForeground(Color.GRAY);
        maskPanel.add(maskLabel);
        maskPanel.add(maskField);
        maskPanel.add(maskHint);

        // Top: mask + table
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(maskPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(table);
        topPanel.add(scrollPane, BorderLayout.CENTER);
        add(topPanel, BorderLayout.CENTER);

        // Side buttons
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton addButton = Theme.createFlatButton("\ucd94\uac00", e -> {
            int num = tableModel.getRowCount() + 1;
            tableModel.addRow(new ReaderConfig("Reader-" + String.format("%02d", num), "192.168.1.151", 20058));
        });
        addButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton deleteButton = Theme.createFlatButton("\uc0ad\uc81c", e -> {
            int row = table.getSelectedRow();
            if (row >= 0) tableModel.removeRow(row);
        });
        deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton upButton = Theme.createFlatButton("\u25b2 \uc704\ub85c", e -> {
            int row = table.getSelectedRow();
            if (row > 0) {
                tableModel.moveRow(row, row - 1);
                table.setRowSelectionInterval(row - 1, row - 1);
            }
        });
        upButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton downButton = Theme.createFlatButton("\u25bc \uc544\ub798\ub85c", e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount() - 1) {
                tableModel.moveRow(row, row + 1);
                table.setRowSelectionInterval(row + 1, row + 1);
            }
        });
        downButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        sidePanel.add(addButton);
        sidePanel.add(Box.createVerticalStrut(5));
        sidePanel.add(deleteButton);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(upButton);
        sidePanel.add(Box.createVerticalStrut(5));
        sidePanel.add(downButton);

        add(sidePanel, BorderLayout.EAST);

        // Bottom: save/cancel
        JPanel bottomPanel = new JPanel(new BorderLayout());

        applyAllCheck = new JCheckBox("\uc804\uccb4\uc801\uc6a9 (1\ud589 \uc124\uc815\uac12\uc744 \uc804\uccb4 \ub9ac\ub354\uae30\uc5d0 \uc801\uc6a9)");
        applyAllCheck.setFont(Theme.SMALL);
        bottomPanel.add(applyAllCheck, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton okButton = Theme.createFlatButton("\uc800\uc7a5", e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            ReaderConfig.setEpcMask(maskField.getText().trim());
            confirmed = true;
            setVisible(false);
        });

        JButton cancelButton = Theme.createFlatButton("\ucde8\uc18c", e -> setVisible(false));

        btnPanel.add(okButton);
        btnPanel.add(cancelButton);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() { return confirmed; }

    public List<ReaderConfig> getConfigs() {
        return tableModel.getConfigs();
    }

    private class ConfigTableModel extends AbstractTableModel {
        private final String[] columns = {"\uc774\ub984", "IP \uc8fc\uc18c", "\ud3ec\ud2b8", "\ubd80\uc800", "\uacbd\uad11\ub4f1", "\ucd9c\ub8251", "\ucd9c\ub8252", "\ucd9c\ub8253", "\ucd9c\ub8254", "\ub4dc\uc6f0\uc2dc\uac04", "\ube44\ud504\uc74c"};
        private final List<ReaderConfig> data;

        ConfigTableModel(List<ReaderConfig> configs) {
            this.data = new ArrayList<>();
            for (ReaderConfig c : configs) {
                ReaderConfig copy = new ReaderConfig(c.getName(), c.getIp(), c.getPort());
                copy.setBuzzerEnabled(c.isBuzzerEnabled());
                copy.setWarningLightEnabled(c.isWarningLightEnabled());
                copy.setAntennaPowers(c.getAntennaPowers().clone());
                copy.setDwellTime(c.getDwellTime());
                copy.setBeepEnabled(c.isBeepEnabled());
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
            if (col == 3 || col == 4 || col == 10) return Boolean.class;
            if (col == 2 || (col >= 5 && col <= 9)) return Integer.class;
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
                case 4: return cfg.isWarningLightEnabled();
                case 5: return cfg.getAntennaPowers()[0];
                case 6: return cfg.getAntennaPowers()[1];
                case 7: return cfg.getAntennaPowers()[2];
                case 8: return cfg.getAntennaPowers()[3];
                case 9: return cfg.getDwellTime();
                case 10: return cfg.isBeepEnabled();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            applySingle(data.get(row), value, col);
            fireTableCellUpdated(row, col);

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
                case 4:
                    cfg.setWarningLightEnabled(Boolean.TRUE.equals(value));
                    break;
                case 5: case 6: case 7: case 8:
                    try {
                        int[] powers = cfg.getAntennaPowers();
                        powers[col - 5] = Integer.parseInt(str);
                        cfg.setAntennaPowers(powers);
                    } catch (NumberFormatException ignored) {}
                    break;
                case 9:
                    try { cfg.setDwellTime(Integer.parseInt(str)); } catch (NumberFormatException ignored) {}
                    break;
                case 10:
                    cfg.setBeepEnabled(Boolean.TRUE.equals(value));
                    break;
            }
        }
    }
}
