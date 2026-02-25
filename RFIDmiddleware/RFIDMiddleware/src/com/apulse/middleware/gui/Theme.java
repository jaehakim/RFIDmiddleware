package com.apulse.middleware.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

public final class Theme {

    private Theme() {}

    // --- Colors ---
    public static final Color HEADER_BG = new Color(0x1B, 0x2A, 0x4A);
    public static final Color HEADER_BG_BOTTOM = new Color(0x24, 0x3B, 0x63);
    public static final Color HEADER_TEXT = new Color(0xE8, 0xEC, 0xF1);
    public static final Color HEADER_BTN_HOVER = new Color(255, 255, 255, 25);
    public static final Color HEADER_SEPARATOR = new Color(255, 255, 255, 40);

    public static final Color CONTENT_BG = new Color(0xF0, 0xF2, 0xF5);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CARD_BORDER = new Color(0xDA, 0xDE, 0xE6);
    public static final Color CARD_SHADOW = new Color(0, 0, 0, 15);
    public static final Color CARD_TEXT = new Color(0x28, 0x2D, 0x37);

    public static final Color TABLE_HEADER_BG = new Color(0xF5, 0xF6, 0xF8);
    public static final Color TABLE_HEADER_FG = new Color(0x4A, 0x55, 0x68);
    public static final Color TABLE_HEADER_BORDER = new Color(0xE2, 0xE5, 0xEA);
    public static final Color TABLE_STRIPE = new Color(0xF8, 0xF9, 0xFA);
    public static final Color TABLE_GRID = new Color(0xEE, 0xEF, 0xF2);

    public static final Color LOG_BG = new Color(0x1A, 0x1D, 0x23);
    public static final Color LOG_FG = new Color(0xD4, 0xD4, 0xD4);

    public static final Color ALERT_BG = new Color(0xFF, 0xE8, 0xE8);
    public static final Color ALERT_FG = new Color(0xB4, 0x00, 0x00);
    public static final Color PERMIT_BG = new Color(0xE8, 0xF5, 0xE8);
    public static final Color PERMIT_FG = new Color(0x00, 0x6E, 0x00);

    public static final Color FLAT_BTN_BORDER = new Color(0xD0, 0xD4, 0xDB);
    public static final Color FLAT_BTN_HOVER = new Color(0xE8, 0xEA, 0xEE);
    public static final Color FLAT_BTN_TEXT = new Color(0x37, 0x3D, 0x4A);

    public static final Color SECTION_LABEL_FG = new Color(0x4A, 0x55, 0x68);

    public static final Color INDICATOR_BUZZER_ON = new Color(30, 150, 220);
    public static final Color INDICATOR_LIGHT_ON = new Color(255, 180, 0);
    public static final Color INDICATOR_OFF = new Color(0xC8, 0xCC, 0xD4);

    // --- Fonts ---
    public static final Font TITLE = new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 16);
    public static final Font SECTION_LABEL = new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 12);
    public static final Font BODY = new Font("\ub9d1\uc740 \uace0\ub515", Font.PLAIN, 12);
    public static final Font SMALL = new Font("\ub9d1\uc740 \uace0\ub515", Font.PLAIN, 11);
    public static final Font CARD_NAME = new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 11);
    public static final Font MONO_SMALL = new Font("Consolas", Font.PLAIN, 9);
    public static final Font LOG = new Font("\ub9d1\uc740 \uace0\ub515", Font.PLAIN, 12);

    // --- Dimensions ---
    public static final int CARD_W = 100;
    public static final int CARD_H = 64;
    public static final int HEADER_H = 52;
    public static final int TABLE_ROW_H = 26;
    public static final int BTN_ROUND = 6;
    public static final int CARD_BAR_W = 6;
    public static final int CARD_ROUND = 8;
    public static final int CARD_GAP = 6;

    // --- Helper: dark header button ---
    public static JButton createHeaderButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(HEADER_BTN_HOVER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), BTN_ROUND, BTN_ROUND);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(BODY);
        btn.setForeground(HEADER_TEXT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        if (action != null) btn.addActionListener(action);
        return btn;
    }

    // --- Helper: flat content button ---
    public static JButton createFlatButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(FLAT_BTN_HOVER);
                } else {
                    g2.setColor(Color.WHITE);
                }
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, BTN_ROUND, BTN_ROUND);
                g2.setColor(FLAT_BTN_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, BTN_ROUND, BTN_ROUND);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(SMALL);
        btn.setForeground(FLAT_BTN_TEXT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        if (action != null) btn.addActionListener(action);
        return btn;
    }

    // --- Helper: style table ---
    public static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setRowHeight(TABLE_ROW_H);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(TABLE_GRID);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setBackground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 12));
        header.setBackground(TABLE_HEADER_BG);
        header.setForeground(TABLE_HEADER_FG);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, TABLE_HEADER_BORDER));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
        header.setOpaque(true);

        // Center-aligned header renderer with background color
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.CENTER);
                setBackground(TABLE_HEADER_BG);
                setForeground(TABLE_HEADER_FG);
                setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 12));
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 1, TABLE_HEADER_BORDER),
                    BorderFactory.createEmptyBorder(0, 4, 0, 4)
                ));
                return this;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
    }

    /** Apply zebra striping via prepareRenderer override - call this AFTER styleTable */
    public static Component applyStripe(JTable table, Component c, boolean isSelected, int row) {
        if (!isSelected) {
            c.setBackground(row % 2 == 0 ? Color.WHITE : TABLE_STRIPE);
        }
        return c;
    }

    // --- Helper: section label ---
    public static JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SECTION_LABEL);
        label.setForeground(SECTION_LABEL_FG);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        return label;
    }
}
