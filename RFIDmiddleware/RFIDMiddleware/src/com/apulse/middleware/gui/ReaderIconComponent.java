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

    private static final int BAR_WIDTH = 5;

    public ReaderIconComponent(int index, String name, String ip, int port) {
        this.readerIndex = index;
        this.readerName = name;
        this.readerIp = ip;
        this.readerPort = port;

        setPreferredSize(new Dimension(90, 62));
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
     * @param currentPowers 현재 저장된 안테나 출력값 (길이 4)
     * @param currentDwellTime 현재 저장된 드웰시간
     */
    public static void showAntennaConfigDialog(Component parent,
                                               int[] currentPowers, int currentDwellTime,
                                               Consumer<int[]> onPowerApply,
                                               java.util.function.BiConsumer<Short, Short> onDwellApply) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 헤더
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

                // 드웰시간: 첫 번째 안테나 값을 대표값으로 사용
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

        // 카드 배경 (상태에 따라 연한 색상)
        Color statusColor = status.getColor();
        if (status == ReaderStatus.READING && !blinkOn) {
            statusColor = statusColor.darker().darker();
        }
        Color bgColor = new Color(
            Math.min(255, statusColor.getRed()   / 5 + 230),
            Math.min(255, statusColor.getGreen() / 5 + 230),
            Math.min(255, statusColor.getBlue()  / 5 + 230)
        );
        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

        // 카드 테두리
        g2.setColor(new Color(200, 200, 200));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

        // 좌측 상태바 (굵은 색상 바)
        g2.setColor(statusColor);
        g2.fillRoundRect(0, 0, BAR_WIDTH + 3, h - 1, 6, 6);
        g2.fillRect(BAR_WIDTH, 0, 4, h);

        int textLeft = BAR_WIDTH + 7;

        // 1행: 리더기 이름
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("맑은 고딕", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(readerName, textLeft, fm.getAscent() + 3);

        // 2행: IP (작은 폰트)
        g2.setFont(new Font("Consolas", Font.PLAIN, 9));
        g2.setColor(new Color(100, 100, 100));
        fm = g2.getFontMetrics();
        int line2Y = fm.getAscent() + 17;
        g2.drawString(readerIp, textLeft, line2Y);

        // 3행: Port
        int line3Y = line2Y + fm.getHeight();
        g2.drawString(":" + readerPort, textLeft, line3Y);

        // 4행: 상태 텍스트 + 경광등
        int line4Y = h - 6;

        // 상태 점 + 텍스트
        g2.setColor(statusColor);
        g2.fillOval(textLeft, line4Y - 8, 8, 8);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(textLeft, line4Y - 8, 8, 8);

        g2.setFont(new Font("맑은 고딕", Font.PLAIN, 9));
        g2.setColor(new Color(60, 60, 60));
        fm = g2.getFontMetrics();
        g2.drawString(status.getLabel(), textLeft + 11, line4Y);

        // 부저 아이콘 (우측 상단)
        int iconX = w - 26;
        int buzzerY = line4Y - 22;

        if (buzzerOn) {
            g2.setColor(new Color(100, 200, 255, 60));
            g2.fillOval(iconX - 2, buzzerY - 2, 12, 12);
            g2.setColor(new Color(30, 150, 220));
        } else {
            g2.setColor(new Color(190, 190, 190));
        }
        g2.fillOval(iconX, buzzerY, 8, 8);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(iconX, buzzerY, 8, 8);

        g2.setFont(new Font("Consolas", Font.PLAIN, 8));
        g2.setColor(buzzerOn ? new Color(30, 120, 180) : Color.GRAY);
        g2.drawString("B", iconX + 10, buzzerY + 8);

        // 경광등 아이콘 (우측 하단)
        int lightY = line4Y - 9;

        if (lightOn) {
            g2.setColor(new Color(255, 200, 50, 60));
            g2.fillOval(iconX - 2, lightY - 2, 12, 12);
            g2.setColor(new Color(255, 180, 0));
        } else {
            g2.setColor(new Color(190, 190, 190));
        }
        g2.fillOval(iconX, lightY, 8, 8);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(iconX, lightY, 8, 8);

        g2.setFont(new Font("Consolas", Font.PLAIN, 8));
        g2.setColor(lightOn ? new Color(200, 130, 0) : Color.GRAY);
        g2.drawString("L", iconX + 10, lightY + 8);

        g2.dispose();
    }
}
