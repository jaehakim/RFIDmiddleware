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
        repaint();
    }

    public void setBuzzerOn(boolean on) {
        this.buzzerOn = on;
        repaint();
    }

    public void setStatus(ReaderStatus status) {
        this.status = status;
        if (status == ReaderStatus.READING) {
            if (!blinkTimer.isRunning()) blinkTimer.start();
        } else {
            blinkTimer.stop();
            blinkOn = true;
        }
        repaint();
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
        int barW = Theme.CARD_BAR_W;

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

        // Left status bar
        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 2, h - 2, r, r));
        g2.setColor(statusColor);
        g2.fillRect(0, 0, barW, h);
        g2.setClip(null);

        int textLeft = barW + 7;
        int midY = h / 2;

        // Row 1: reader name (top, smaller font)
        g2.setColor(Theme.CARD_TEXT);
        g2.setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(readerName, textLeft, fm.getAscent() + 3);

        // Row 2: status circle + label (center-bottom area)
        int circleD = 22;
        int circleCY = midY + 9;
        int circleX = textLeft;
        int circleY = circleCY - circleD / 2;

        // Glow/pulse for READING state
        if (status == ReaderStatus.READING) {
            int glowAlpha = blinkOn ? 50 : 20;
            int glowSize = blinkOn ? 8 : 4;
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
        g2.setColor(new Color(255, 255, 255, 70));
        g2.fillOval(circleX + 3, circleY + 2, circleD / 2, circleD / 2 - 1);

        // Status label (right of circle, smaller font)
        g2.setFont(new Font("\ub9d1\uc740 \uace0\ub515", Font.PLAIN, 8));
        fm = g2.getFontMetrics();
        g2.setColor(Theme.CARD_TEXT);
        g2.drawString(status.getLabel(), circleX + circleD + 3, circleCY + fm.getAscent() / 2 - 1);

        // B / L indicators (right side, vertical stack, larger)
        int indSize = 12;
        int indX = w - indSize - 8;

        // Buzzer indicator (top)
        int buzY = midY - indSize - 3;
        if (buzzerOn) {
            g2.setColor(new Color(100, 200, 255, 60));
            g2.fillOval(indX - 4, buzY - 4, indSize + 8, indSize + 8);
            g2.setColor(Theme.INDICATOR_BUZZER_ON);
        } else {
            g2.setColor(Theme.INDICATOR_OFF);
        }
        g2.fillOval(indX, buzY, indSize, indSize);

        // Light indicator (bottom)
        int lightY = midY + 3;
        if (lightOn) {
            g2.setColor(new Color(255, 200, 50, 60));
            g2.fillOval(indX - 4, lightY - 4, indSize + 8, indSize + 8);
            g2.setColor(Theme.INDICATOR_LIGHT_ON);
        } else {
            g2.setColor(Theme.INDICATOR_OFF);
        }
        g2.fillOval(indX, lightY, indSize, indSize);

        g2.dispose();
    }
}
