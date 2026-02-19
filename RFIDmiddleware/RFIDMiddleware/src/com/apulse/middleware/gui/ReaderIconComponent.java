package com.apulse.middleware.gui;

import com.apulse.middleware.reader.ReaderStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ReaderIconComponent extends JPanel {
    private final int readerIndex;
    private final String readerName;
    private final String readerIp;
    private final int readerPort;
    private ReaderStatus status = ReaderStatus.DISCONNECTED;
    private boolean lightOn = false;
    private boolean blinkOn = true;
    private Timer blinkTimer;
    private JPopupMenu popupMenu;
    private Runnable onConnect;
    private Runnable onDisconnect;
    private Runnable onStartInventory;
    private Runnable onStopInventory;
    private Runnable onLightOn;
    private Runnable onLightOff;

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
    }

    private void showPopup(MouseEvent e) {
        popupMenu.show(this, e.getX(), e.getY());
    }

    public void setActions(Runnable onConnect, Runnable onDisconnect,
                           Runnable onStartInventory, Runnable onStopInventory,
                           Runnable onLightOn, Runnable onLightOff) {
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.onStartInventory = onStartInventory;
        this.onStopInventory = onStopInventory;
        this.onLightOn = onLightOn;
        this.onLightOff = onLightOff;
    }

    public void setLightOn(boolean on) {
        this.lightOn = on;
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

        // 경광등 아이콘 (우측)
        int lightX = w - 30;
        int lightY = line4Y - 9;

        // 경광등 원형 (크게)
        if (lightOn) {
            // ON: 주황색 글로우 효과
            g2.setColor(new Color(255, 200, 50, 60));
            g2.fillOval(lightX - 3, lightY - 3, 16, 16);
            g2.setColor(new Color(255, 180, 0));
        } else {
            g2.setColor(new Color(190, 190, 190));
        }
        g2.fillOval(lightX, lightY, 10, 10);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(lightX, lightY, 10, 10);

        // 경광등 라벨
        g2.setFont(new Font("맑은 고딕", Font.PLAIN, 8));
        fm = g2.getFontMetrics();
        String lightLabel = lightOn ? "ON" : "OFF";
        g2.setColor(lightOn ? new Color(200, 130, 0) : Color.GRAY);
        g2.drawString(lightLabel, lightX + 12, line4Y);

        g2.dispose();
    }
}
