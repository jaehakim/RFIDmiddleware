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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TagDataPanel extends JPanel {
    private final TagTableModel tableModel;
    private final JTable table;
    private final JLabel countLabel;
    private final JCheckBox deduplicateCheck;

    private Cache<String, Boolean> dbDedupCache;
    private final Map<String, TagData> tagDedup = new LinkedHashMap<>();
    private final List<TagData> tagList = new ArrayList<>();

    public static final String STATUS_PERMITTED = "\ubc18\ucd9c\ud5c8\uc6a9";
    public static final String STATUS_ALERT = "\ubc18\ucd9c\uc54c\ub9bc";

    public TagDataPanel() {
        this(30, 10000);
    }

    public TagDataPanel(int cacheTtlSeconds, int cacheMaxSize) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.CONTENT_BG);

        // Section label instead of TitledBorder
        add(Theme.createSectionLabel("\ud0dc\uadf8 \ub370\uc774\ud130"), BorderLayout.NORTH);

        dbDedupCache = Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(cacheMaxSize)
            .build();

        tableModel = new TagTableModel();
        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                // Zebra stripe for non-status rows handled in StatusAwareRenderer
                return c;
            }
        };
        Theme.styleTable(table);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(130);  // time
        table.getColumnModel().getColumn(1).setPreferredWidth(60);   // reader
        table.getColumnModel().getColumn(2).setPreferredWidth(200);  // EPC
        table.getColumnModel().getColumn(3).setPreferredWidth(35);   // RSSI
        table.getColumnModel().getColumn(4).setPreferredWidth(40);   // antenna
        table.getColumnModel().getColumn(5).setPreferredWidth(35);   // count
        table.getColumnModel().getColumn(6).setPreferredWidth(80);   // asset number
        table.getColumnModel().getColumn(7).setPreferredWidth(100);  // asset name
        table.getColumnModel().getColumn(8).setPreferredWidth(70);   // department
        table.getColumnModel().getColumn(9).setPreferredWidth(65);   // status

        // Status-aware renderer
        StatusAwareRenderer renderer = new StatusAwareRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);

        // Wrap table in center panel
        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setOpaque(false);
        tableWrapper.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with separator line
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(true);
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.TABLE_GRID),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        countLabel = new JLabel("Tags: 0");
        countLabel.setFont(Theme.SMALL);
        countLabel.setForeground(Theme.SECTION_LABEL_FG);

        deduplicateCheck = new JCheckBox("\uc911\ubcf5\uc81c\uac70", true);
        deduplicateCheck.setFont(Theme.SMALL);
        deduplicateCheck.setOpaque(false);
        deduplicateCheck.addActionListener(e -> onDeduplicateToggle());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(countLabel);
        leftPanel.add(deduplicateCheck);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        JButton clearButton = Theme.createFlatButton("\ucd08\uae30\ud654", e -> clearTags());
        JButton exportButton = Theme.createFlatButton("\uc5d1\uc140 \uc800\uc7a5", e -> exportToExcel());
        JButton dbQueryButton = Theme.createFlatButton("DB \uc870\ud68c", e -> showDbQueryDialog());

        rightPanel.add(clearButton);
        rightPanel.add(exportButton);
        rightPanel.add(dbQueryButton);

        bottomPanel.add(leftPanel, BorderLayout.WEST);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        tableWrapper.add(bottomPanel, BorderLayout.SOUTH);

        // Use a wrapper between section label and table
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(tableWrapper, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    public boolean addTag(String readerName, String epc, int rssi, int antenna,
                          String assetNumber, String assetName, String department,
                          String assetStatus) {
        String time = HexUtils.nowShort();

        boolean isNew = (dbDedupCache.getIfPresent(epc) == null);
        if (isNew) {
            dbDedupCache.put(epc, Boolean.TRUE);
        }

        TagData existing = tagDedup.get(epc);
        if (existing != null) {
            existing.update(rssi, antenna, time, readerName);
            existing.setAssetInfo(assetNumber, assetName, department, assetStatus);
        } else {
            TagData newTag = new TagData(epc, readerName, rssi, antenna, time);
            newTag.setAssetInfo(assetNumber, assetName, department, assetStatus);
            tagDedup.put(epc, newTag);
        }

        TagData listTag = new TagData(epc, readerName, rssi, antenna, time);
        listTag.setAssetInfo(assetNumber, assetName, department, assetStatus);
        tagList.add(listTag);

        tableModel.refresh();
        updateCountLabel();
        return isNew;
    }

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

    private List<TagData> getCurrentData() {
        if (isDeduplicateMode()) {
            return new ArrayList<>(tagDedup.values());
        } else {
            return new ArrayList<>(tagList);
        }
    }

    public Set<String> getDbDedupCacheKeys() {
        return new HashSet<>(dbDedupCache.asMap().keySet());
    }

    public long getDbDedupCacheSize() {
        return dbDedupCache.estimatedSize();
    }

    public void clearTags() {
        dbDedupCache.invalidateAll();
        tagDedup.clear();
        tagList.clear();
        tableModel.refresh();
        updateCountLabel();
    }

    private void showDbQueryDialog() {
        if (!DatabaseManager.getInstance().isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "\ub370\uc774\ud130\ubca0\uc774\uc2a4\uc5d0 \uc5f0\uacb0\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.",
                "DB \uc870\ud68c", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        JTextField fromField = new JTextField(today + " 00:00:00");
        JTextField toField = new JTextField(sdf.format(new Date()));

        panel.add(new JLabel("\uc2dc\uc791 \uc2dc\uac04:"));
        panel.add(fromField);
        panel.add(new JLabel("\uc885\ub8cc \uc2dc\uac04:"));
        panel.add(toField);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "DB \uc870\ud68c - \uae30\uac04 \uc120\ud0dd", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        List<TagData> dbData = TagRepository.getInstance()
            .getTagReads(fromField.getText().trim(), toField.getText().trim());

        if (dbData.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "\uc870\ud68c\ub41c \ub370\uc774\ud130\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.",
                "DB \uc870\ud68c", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] cols = {"\uc2dc\uac04", "\ub9ac\ub354\uae30", "EPC", "RSSI", "\uc548\ud14c\ub098"};
        Object[][] rows = new Object[dbData.size()][5];
        for (int i = 0; i < dbData.size(); i++) {
            TagData t = dbData.get(i);
            rows[i][0] = t.getLastSeen();
            rows[i][1] = t.getReaderName();
            rows[i][2] = t.getEpc();
            rows[i][3] = t.getRssi();
            rows[i][4] = t.getAntenna();
        }

        JTable resultTable = new JTable(rows, cols);
        Theme.styleTable(resultTable);

        JScrollPane sp = new JScrollPane(resultTable);
        sp.setPreferredSize(new Dimension(600, 400));

        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(this), sp,
            "DB \uc870\ud68c \uacb0\uacfc (" + dbData.size() + "\uac74)", JOptionPane.PLAIN_MESSAGE);
    }

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
                pw.println("  <td>" + tag.getAntenna() + "</td>");
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
        private final String[] columns = {"\uc2dc\uac04", "\ub9ac\ub354\uae30", "EPC", "RSSI", "\uc548\ud14c\ub098", "\ud69f\uc218", "\uc790\uc0b0\ubc88\ud638", "\uc790\uc0b0\uba85", "\ubd80\uc11c", "\uc0c1\ud0dc"};
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
                case 4: return tag.getAntenna();
                case 5: return tag.getCount();
                case 6: return tag.getAssetNumber() != null ? tag.getAssetNumber() : "";
                case 7: return tag.getAssetName() != null ? tag.getAssetName() : "";
                case 8: return tag.getDepartment() != null ? tag.getDepartment() : "";
                case 9: return tag.getAssetStatus() != null ? tag.getAssetStatus() : "";
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 3 || col == 4 || col == 5) return Integer.class;
            return String.class;
        }

        String getStatusAt(int row) {
            if (row >= 0 && row < data.size()) {
                return data.get(row).getAssetStatus();
            }
            return null;
        }
    }

    private class StatusAwareRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                String status = tableModel.getStatusAt(modelRow);
                if (STATUS_ALERT.equals(status)) {
                    c.setBackground(Theme.ALERT_BG);
                    c.setForeground(Theme.ALERT_FG);
                } else if (STATUS_PERMITTED.equals(status)) {
                    c.setBackground(Theme.PERMIT_BG);
                    c.setForeground(Theme.PERMIT_FG);
                } else {
                    // Zebra stripe for normal rows
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Theme.TABLE_STRIPE);
                    c.setForeground(Theme.CARD_TEXT);
                }
            }

            // Center alignment for specific columns
            if (column == 0 || column == 3 || column == 4 || column == 5 || column == 6 || column == 8 || column == 9) {
                setHorizontalAlignment(JLabel.CENTER);
            } else {
                setHorizontalAlignment(JLabel.LEFT);
            }

            return c;
        }
    }
}
