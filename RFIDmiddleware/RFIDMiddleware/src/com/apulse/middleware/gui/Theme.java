package com.apulse.middleware.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;

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

    // --- Header icon factory ---
    private static final int ICON_SIZE = 14;

    public static Icon createHeaderIcon(String type) {
        return new Icon() {
            @Override
            public int getIconWidth() { return ICON_SIZE; }
            @Override
            public int getIconHeight() { return ICON_SIZE; }
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(HEADER_TEXT);
                int s = ICON_SIZE;

                switch (type) {
                    case "connect": // 플러그: 원 + 방사선
                        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.fillOval(4, 4, 6, 6);
                        g2.drawLine(7, 1, 7, 3);
                        g2.drawLine(7, 11, 7, 13);
                        g2.drawLine(1, 7, 3, 7);
                        g2.drawLine(11, 7, 13, 7);
                        break;

                    case "disconnect": // 끊김: 빈 원 + 대각선 슬래시
                        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawOval(3, 3, 8, 8);
                        g2.drawLine(2, 12, 12, 2);
                        break;

                    case "play": // 재생 삼각형
                        GeneralPath tri = new GeneralPath();
                        tri.moveTo(3, 1);
                        tri.lineTo(13, 7);
                        tri.lineTo(3, 13);
                        tri.closePath();
                        g2.fill(tri);
                        break;

                    case "stop": // 정지 사각형
                        g2.fillRoundRect(2, 2, 10, 10, 2, 2);
                        break;

                    case "clear": // 순환 화살표 (리셋)
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(new Arc2D.Double(2, 2, 10, 10, 30, 280, Arc2D.OPEN));
                        // 화살표 머리
                        int ax = 10, ay = 4;
                        g2.drawLine(ax, ay, ax + 3, ay);
                        g2.drawLine(ax, ay, ax, ay - 3);
                        break;

                    case "database": // DB 실린더 (3단)
                        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int dw = 10, dh = 3, dx = 2;
                        // 상단 타원
                        g2.drawOval(dx, 1, dw, dh);
                        // 좌우 세로선
                        g2.drawLine(dx, 2 + dh / 2, dx, 11);
                        g2.drawLine(dx + dw, 2 + dh / 2, dx + dw, 11);
                        // 중간 곡선
                        g2.drawArc(dx, 5, dw, dh, 180, 180);
                        // 하단 타원
                        g2.drawArc(dx, 9, dw, dh, 180, 180);
                        break;

                    case "help": // 물음표 원
                        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawOval(1, 1, 12, 12);
                        g2.setFont(new Font("Arial", Font.BOLD, 10));
                        FontMetrics fm = g2.getFontMetrics();
                        String q = "?";
                        g2.drawString(q, (s - fm.stringWidth(q)) / 2, fm.getAscent() + 1);
                        break;

                    case "settings": // 톱니바퀴
                        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawOval(4, 4, 6, 6);
                        double cx = 7, cy = 7;
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.PI * 2 * i / 8;
                            int x1 = (int) (cx + 4.5 * Math.cos(angle));
                            int y1 = (int) (cy + 4.5 * Math.sin(angle));
                            int x2 = (int) (cx + 6.5 * Math.cos(angle));
                            int y2 = (int) (cy + 6.5 * Math.sin(angle));
                            g2.drawLine(x1, y1, x2, y2);
                        }
                        break;
                }
                g2.dispose();
            }
        };
    }

    // --- Helper: dark header button ---
    public static JButton createHeaderButton(String text, java.awt.event.ActionListener action) {
        return createHeaderButton(text, null, action);
    }

    public static JButton createHeaderButton(String text, Icon icon, java.awt.event.ActionListener action) {
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
        if (icon != null) {
            btn.setIcon(icon);
            btn.setIconTextGap(5);
            btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 12));
        } else {
            btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        }
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
