package com.apulse.middleware.gui;

import com.apulse.middleware.reader.ReaderStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class ReaderIconComponent extends JPanel {
    private final int readerIndex;
    private final String readerName;
    private final String readerIp;
    private final int readerPort;
    private ReaderStatus status = ReaderStatus.DISCONNECTED;
    private boolean lightOn = false;
    private boolean buzzerOn = false;
    private boolean blinkOn = true;
    private boolean beepEnabled = false;
    private boolean buzzerConfigEnabled = false;
    private boolean lightConfigEnabled = false;
    private Timer blinkTimer;
    private JPopupMenu popupMenu;
    private Runnable onConnect;
    private Runnable onDisconnect;
    private Runnable onStartInventory;
    private Runnable onStopInventory;
    private Runnable onLightOn;
    private Runnable onLightOff;
    private Runnable onBuzzerOn;
    private Runnable onBuzzerOff;
    private Runnable onAntennaConfig;

    public ReaderIconComponent(int index, String name, String ip, int port) {
        this.readerIndex = index;
        this.readerName = name;
        this.readerIp = ip;
        this.readerPort = port;

        setPreferredSize(new Dimension(Theme.CARD_W, Theme.CARD_H));
        setOpaque(false);
        setToolTipText(name + " (" + ip + ":" + port + ")");

        blinkTimer = new Timer(500, e -> {
            blinkOn = !blinkOn;
            repaint();
        });

        buildPopupMenu();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
        });
    }

    private void buildPopupMenu() {
        popupMenu = new JPopupMenu();

        JMenuItem connectItem = new JMenuItem("연결");
        connectItem.addActionListener(e -> { if (onConnect != null) onConnect.run(); });
        popupMenu.add(connectItem);

        JMenuItem disconnectItem = new JMenuItem("연결 해제");
        disconnectItem.addActionListener(e -> { if (onDisconnect != null) onDisconnect.run(); });
        popupMenu.add(disconnectItem);

        popupMenu.addSeparator();

        JMenuItem startItem = new JMenuItem("인벤토리 시작");
        startItem.addActionListener(e -> { if (onStartInventory != null) onStartInventory.run(); });
        popupMenu.add(startItem);

        JMenuItem stopItem = new JMenuItem("인벤토리 중지");
        stopItem.addActionListener(e -> { if (onStopInventory != null) onStopInventory.run(); });
        popupMenu.add(stopItem);

        popupMenu.addSeparator();

        JMenuItem lightOnItem = new JMenuItem("경광등 ON");
        lightOnItem.addActionListener(e -> { if (onLightOn != null) onLightOn.run(); });
        popupMenu.add(lightOnItem);

        JMenuItem lightOffItem = new JMenuItem("경광등 OFF");
        lightOffItem.addActionListener(e -> { if (onLightOff != null) onLightOff.run(); });
        popupMenu.add(lightOffItem);

        JMenuItem buzzerOnItem = new JMenuItem("부저 ON");
        buzzerOnItem.addActionListener(e -> { if (onBuzzerOn != null) onBuzzerOn.run(); });
        popupMenu.add(buzzerOnItem);

        JMenuItem buzzerOffItem = new JMenuItem("부저 OFF");
        buzzerOffItem.addActionListener(e -> { if (onBuzzerOff != null) onBuzzerOff.run(); });
        popupMenu.add(buzzerOffItem);

        popupMenu.addSeparator();

        JMenuItem antennaConfigItem = new JMenuItem("안테나 설정");
        antennaConfigItem.addActionListener(e -> { if (onAntennaConfig != null) onAntennaConfig.run(); });
        popupMenu.add(antennaConfigItem);
    }

    private void showPopup(MouseEvent e) {
        popupMenu.show(this, e.getX(), e.getY());
    }

    public void setActions(Runnable onConnect, Runnable onDisconnect,
                           Runnable onStartInventory, Runnable onStopInventory,
                           Runnable onLightOn, Runnable onLightOff,
                           Runnable onBuzzerOn, Runnable onBuzzerOff,
                           Runnable onAntennaConfig) {
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.onStartInventory = onStartInventory;
        this.onStopInventory = onStopInventory;
        this.onLightOn = onLightOn;
        this.onLightOff = onLightOff;
        this.onBuzzerOn = onBuzzerOn;
        this.onBuzzerOff = onBuzzerOff;
        this.onAntennaConfig = onAntennaConfig;
    }

    public void setLightOn(boolean on) {
        this.lightOn = on;
        updateBlinkTimer();
        repaint();
    }

    public void setBuzzerOn(boolean on) {
        this.buzzerOn = on;
        updateBlinkTimer();
        repaint();
    }

    public void setStatus(ReaderStatus status) {
        this.status = status;
        updateBlinkTimer();
        repaint();
    }

    public void setBeepEnabled(boolean enabled) {
        this.beepEnabled = enabled;
        updateBlinkTimer();
        repaint();
    }

    public void setConfigState(boolean buzzerConfig, boolean lightConfig, boolean beepConfig) {
        this.buzzerConfigEnabled = buzzerConfig;
        this.lightConfigEnabled = lightConfig;
        this.beepEnabled = beepConfig;
        updateBlinkTimer();
        repaint();
    }

    private void updateBlinkTimer() {
        boolean needBlink = (status == ReaderStatus.READING) || buzzerOn || lightOn;
        if (needBlink) {
            if (!blinkTimer.isRunning()) blinkTimer.start();
        } else {
            blinkTimer.stop();
            blinkOn = true;
        }
    }

    /**
     * 안테나 설정 다이얼로그 표시.
     */
    public static void showAntennaConfigDialog(Component parent,
                                               int[] currentPowers, int currentDwellTime,
                                               Consumer<int[]> onPowerApply,
                                               java.util.function.BiConsumer<Short, Short> onDwellApply) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0;
        gbc.gridx = 0; panel.add(new JLabel("안테나"), gbc);
        gbc.gridx = 1; panel.add(new JLabel("출력(dBm)"), gbc);
        gbc.gridx = 2; panel.add(new JLabel("드웰시간(ms)"), gbc);

        JTextField[] powerFields = new JTextField[4];
        JTextField[] dwellFields = new JTextField[4];

        for (int i = 0; i < 4; i++) {
            gbc.gridy = i + 1;
            gbc.gridx = 0;
            panel.add(new JLabel(String.valueOf(i + 1)), gbc);

            gbc.gridx = 1;
            powerFields[i] = new JTextField(String.valueOf(currentPowers[i]), 5);
            panel.add(powerFields[i], gbc);

            gbc.gridx = 2;
            dwellFields[i] = new JTextField(String.valueOf(currentDwellTime), 5);
            panel.add(dwellFields[i], gbc);
        }

        int result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(parent),
            panel, "안테나 설정",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                int[] powers = new int[4];
                for (int i = 0; i < 4; i++) {
                    powers[i] = Integer.parseInt(powerFields[i].getText().trim());
                }
                if (onPowerApply != null) {
                    onPowerApply.accept(powers);
                }

                short onTime = Short.parseShort(dwellFields[0].getText().trim());
                short offTime = 0;
                if (onDwellApply != null) {
                    onDwellApply.accept(onTime, offTime);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(parent),
                    "숫자를 올바르게 입력해주세요.", "입력 오류",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int r = Theme.CARD_ROUND;

        Color statusColor = status.getColor();

        // Shadow (1px offset)
        g2.setColor(Theme.CARD_SHADOW);
        g2.fillRoundRect(1, 1, w - 1, h - 1, r, r);

        // White card background
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 2, h - 2, r, r);

        // Thin border
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 2, h - 2, r, r);

        // Status circle (centered in card, excluding indicator area)
        int circleD = 32;
        int contentRight = w - 16;
        int circleCX = contentRight / 2;
        int circleCY = h / 2;
        int circleX = circleCX - circleD / 2;
        int circleY = circleCY - circleD / 2;

        // Glow/pulse for READING state
        if (status == ReaderStatus.READING) {
            int glowAlpha = blinkOn ? 40 : 15;
            int glowSize = blinkOn ? 4 : 2;
            g2.setColor(new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), glowAlpha));
            g2.fillOval(circleX - glowSize, circleY - glowSize, circleD + glowSize * 2, circleD + glowSize * 2);
        }

        // Main status circle
        Color circleColor = statusColor;
        if (status == ReaderStatus.READING && !blinkOn) {
            circleColor = new Color(
                Math.max(statusColor.getRed() - 40, 0),
                Math.max(statusColor.getGreen() - 40, 0),
                Math.max(statusColor.getBlue() - 40, 0));
        }
        g2.setColor(circleColor);
        g2.fillOval(circleX, circleY, circleD, circleD);

        // Inner highlight
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillOval(circleX + 2, circleY + 2, circleD / 2, circleD / 2 - 1);

        // Reader index number inside circle
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 11));
        String indexStr = String.valueOf(readerIndex + 1);
        FontMetrics fm = g2.getFontMetrics();
        int textX = circleCX - fm.stringWidth(indexStr) / 2;
        int textY = circleCY + fm.getAscent() / 2 - 1;
        g2.drawString(indexStr, textX, textY);

        // 3 indicators: beep / light / buzzer (vertical, right side, compact)
        int indSize = 9;
        int indGap = 2;
        int totalInd = indSize * 3 + indGap * 2;
        int indX = w - indSize - 6;
        int indStartY = (h - totalInd) / 2;

        // 1) beep - speaker icon
        Color beepColor = new Color(0x2E, 0xCC, 0x71);
        boolean beepActive = beepEnabled && status == ReaderStatus.READING;
        drawBeepIcon(g2, indX, indStartY, indSize, beepEnabled, beepActive, beepColor);

        // 2) light - warning light icon
        int lightIY = indStartY + indSize + indGap;
        drawLightIcon(g2, indX, lightIY, indSize, lightConfigEnabled, lightOn, Theme.INDICATOR_LIGHT_ON);

        // 3) buzzer - bell icon
        int buzzerIY = lightIY + indSize + indGap;
        drawBellIcon(g2, indX, buzzerIY, indSize, buzzerConfigEnabled, buzzerOn, Theme.INDICATOR_BUZZER_ON);

        g2.dispose();
    }

    private Color getDrawColor(boolean configOn, boolean active, Color color) {
        if (!configOn) return Theme.INDICATOR_OFF;
        if (active) {
            return blinkOn ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 80);
        }
        return color;
    }

    private void drawGlow(Graphics2D g2, int cx, int cy, int size, Color color) {
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        g2.fillOval(cx - size / 2 - 2, cy - size / 2 - 2, size + 4, size + 4);
    }

    /** Speaker icon for beep */
    private void drawBeepIcon(Graphics2D g2, int x, int y, int s, boolean configOn, boolean active, Color color) {
        Color c = getDrawColor(configOn, active, color);
        if (active && blinkOn) drawGlow(g2, x + s / 2, y + s / 2, s, color);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Speaker body
        int bx = x + 1, by = y + s / 2 - 2;
        g2.fillRect(bx, by, 3, 4);
        // Speaker cone
        int[] cxs = {bx + 3, bx + s - 3, bx + s - 3, bx + 3};
        int[] cys = {by, y, y + s - 1, by + 4};
        g2.fillPolygon(cxs, cys, 4);
        // Sound waves
        if (configOn) {
            g2.drawArc(x + s - 4, y + 1, 4, s - 2, -45, 90);
        }
    }

    /** Warning light / beacon icon */
    private void drawLightIcon(Graphics2D g2, int x, int y, int s, boolean configOn, boolean active, Color color) {
        Color c = getDrawColor(configOn, active, color);
        if (active && blinkOn) drawGlow(g2, x + s / 2, y + s / 2, s, color);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + s / 2;
        // Light dome (triangle top)
        int[] txs = {cx, x + s - 1, x + 1};
        int[] tys = {y + 1, y + s - 3, y + s - 3};
        g2.fillPolygon(txs, tys, 3);
        // Base bar
        g2.fillRect(x + 1, y + s - 2, s - 2, 2);
        // Rays when active
        if (configOn) {
            g2.drawLine(cx, y - 1, cx, y);
            g2.drawLine(x - 1, y + s / 2 - 1, x, y + s / 2 - 1);
            g2.drawLine(x + s - 1, y + s / 2 - 1, x + s, y + s / 2 - 1);
        }
    }

    /** Bell icon for buzzer */
    private void drawBellIcon(Graphics2D g2, int x, int y, int s, boolean configOn, boolean active, Color color) {
        Color c = getDrawColor(configOn, active, color);
        if (active && blinkOn) drawGlow(g2, x + s / 2, y + s / 2, s, color);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + s / 2;
        // Bell dome
        g2.fillArc(x + 1, y + 1, s - 2, s - 2, 0, 180);
        // Bell body
        g2.fillRect(x + 1, y + s / 2, s - 2, s / 2 - 2);
        // Bell rim
        g2.fillRect(x, y + s - 3, s, 2);
        // Clapper
        g2.fillOval(cx - 1, y + s - 2, 2, 2);
    }
}
