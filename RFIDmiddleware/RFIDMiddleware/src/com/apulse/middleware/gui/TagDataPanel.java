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

        // Section label with help button
        add(Theme.createSectionHeader("[ \ud0dc\uadf8 \ub370\uc774\ud130 ]",
            "\ud0dc\uadf8 \ub370\uc774\ud130 - \ub3c4\uc6c0\ub9d0",
            "\u25a0 \ud0dc\uadf8 \ub370\uc774\ud130 \ud328\ub110\n\n"
            + "RFID \ub9ac\ub354\uae30\uc5d0\uc11c \uac10\uc9c0\ub41c \ud0dc\uadf8 \uc815\ubcf4\ub97c \uc2e4\uc2dc\uac04\uc73c\ub85c \ud45c\uc2dc\ud569\ub2c8\ub2e4.\n\n"
            + "\u25b6 \ud14c\uc774\ube14 \ucee8\ub7fc\n"
            + "  \u2022 \uc2dc\uac04: \ub9c8\uc9c0\ub9c9 \ud0dc\uadf8 \uc77d\uae30 \uc2dc\uac01\n"
            + "  \u2022 \ub9ac\ub354\uae30: \ud0dc\uadf8\ub97c \uc77d\uc740 \ub9ac\ub354\uae30\uba85\n"
            + "  \u2022 EPC: \ud0dc\uadf8 \uace0\uc720 \uc2dd\ubcc4\ucf54\ub4dc (HEX)\n"
            + "  \u2022 RSSI: \uc218\uc2e0 \uc2e0\ud638 \uac15\ub3c4 (dBm, \uac12\uc774 \ud074\uc218\ub85d \uac15\ud568)\n"
            + "  \u2022 \uc548\ud14c\ub098: \ud0dc\uadf8\ub97c \uac10\uc9c0\ud55c \uc548\ud14c\ub098 \ubc88\ud638\n"
            + "  \u2022 \ud69f\uc218: \uc911\ubcf5\uc81c\uac70 \ubaa8\ub4dc\uc5d0\uc11c \ub3d9\uc77c EPC \uc77d\uae30 \ud69f\uc218\n"
            + "  \u2022 \uc790\uc0b0\ubc88\ud638/\uc790\uc0b0\uba85/\ubd80\uc11c: DB \uc790\uc0b0 \ub9e4\uce6d \uc815\ubcf4\n"
            + "  \u2022 \uc0c1\ud0dc: \ubc18\ucd9c\ud5c8\uc6a9/\ubc18\ucd9c\uc54c\ub9bc \ud45c\uc2dc\n\n"
            + "\u25b6 \ud589 \uc0c9\uc0c1\n"
            + "  \u2022 \ube68\uac04 \ubc30\uacbd: \ubbf8\ud5c8\uac00 \ubc18\ucd9c \uac10\uc9c0 (\uc790\uc0b0\uc774\uc9c0\ub9cc \ubc18\ucd9c\ud5c8\uc6a9 \uc5c6\uc74c)\n"
            + "  \u2022 \ucd08\ub85d \ubc30\uacbd: \ubc18\ucd9c\ud5c8\uc6a9\ub41c \uc790\uc0b0 (\uc815\uc0c1 \ubc18\ucd9c)\n"
            + "  \u2022 \ud770\uc0c9/\ud68c\uc0c9 \ubc88\uac08\uc544: \uc77c\ubc18 \ud0dc\uadf8\n\n"
            + "\u25b6 \ud558\ub2e8 \ubc84\ud2bc\n"
            + "  \u2022 \uc911\ubcf5\uc81c\uac70: \ub3d9\uc77c EPC \ud0dc\uadf8 \ubcd1\ud569 \ud45c\uc2dc (ON/OFF)\n"
            + "  \u2022 \ucd08\uae30\ud654: \ud14c\uc774\ube14 \ub370\uc774\ud130 \uc804\uccb4 \uc0ad\uc81c\n"
            + "  \u2022 \uc5d1\uc140 \uc800\uc7a5: \ud604\uc7ac \ub370\uc774\ud130\ub97c .xls \ud30c\uc77c\ub85c \ub0b4\ubcf4\ub0b4\uae30\n"
            + "  \u2022 DB \uc870\ud68c: \uae30\uac04\ubcc4 tag_reads \ud14c\uc774\ube14 \uc870\ud68c\n\n"
            + "\u25b6 \ud654\uba74 \ud45c\uc2dc vs DB \uc800\uc7a5\n"
            + "  \u2022 \ud654\uba74 \uadf8\ub9ac\ub4dc: \ud0dc\uadf8 \uac10\uc9c0 \uc989\uc2dc \uc2e4\uc2dc\uac04 \ud45c\uc2dc\n"
            + "  \u2022 DB \uc800\uc7a5: \uc989\uc2dc \uc800\uc7a5\uc774 \uc544\ub2cc \ubaa8\uc544\uc11c \ubc30\uce58 \uc800\uc7a5\n\n"
            + "\u25b6 DB \uc800\uc7a5 \uaddc\uce59 (tag_reads \ud14c\uc774\ube14)\n"
            + "  1) \ud0dc\uadf8 \uac10\uc9c0 \uc2dc Caffeine \uce90\uc2dc\ub85c \uc911\ubcf5 \uccb4\ud06c\n"
            + "     - \ub3d9\uc77c EPC\uac00 TTL(\uae30\ubcf8 30\ucd08) \uc774\ub0b4 \uc7ac\uac10\uc9c0\n"
            + "       \u2192 \ud654\uba74\uc5d0\ub9cc \uc5c5\ub370\uc774\ud2b8, DB \uc800\uc7a5 \uc548 \ud568\n"
            + "     - TTL \ub9cc\ub8cc \ud6c4 \ub3d9\uc77c EPC \uc7ac\uac10\uc9c0\n"
            + "       \u2192 \uc2e0\uaddc \ub370\uc774\ud130\ub85c DB INSERT\n"
            + "  2) \uc2e0\uaddc \ud0dc\uadf8\ub9cc BlockingQueue\uc5d0 \ub123\uc74c (\uc989\uc2dc DB \uc4f0\uae30 \uc544\ub2d8)\n"
            + "  3) TagDB-Writer \ubc31\uadf8\ub77c\uc6b4\ub4dc \uc2a4\ub808\ub4dc\uac00 \ubc30\uce58 \ucc98\ub9ac\n"
            + "     - 50\uac74 \ubaa8\uc774\uac70\ub098 500ms \uacbd\uacfc \uc2dc executeBatch()\n"
            + "  4) \uc800\uc7a5 \ucee8\ub7fc: EPC, \ub9ac\ub354\uae30\uba85, RSSI, \uc548\ud14c\ub098, \uc77d\uae30\uc2dc\uac01\n"
            + "  5) \uc885\ub8cc \uc2dc \ub300\uae30 \uc911\uc778 \ub370\uc774\ud130 \uc790\ub3d9 flush"
        ), BorderLayout.NORTH);

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
            JOptionPane.showMessageDialog(this, "\ub0b4\ubcf4\ub0bc \ud0dc\uadf8 \ub370\uc774\ud130\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.",
                "\uc54c\ub9bc", JOptionPane.INFORMATION_MESSAGE);
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
                "\uc800\uc7a5 \uc644\ub8cc: " + file.getAbsolutePath() + "\n(" + data.size() + "\uac74)",
                "\uc5d1\uc140 \uc800\uc7a5", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "\uc800\uc7a5 \uc2e4\ud328: " + ex.getMessage(),
                "\uc624\ub958", JOptionPane.ERROR_MESSAGE);
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
