package com.apulse.middleware.gui;

import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.reader.ReaderManager;
import com.apulse.middleware.reader.ReaderStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public class ReaderStatusPanel extends JPanel {
    private final List<ReaderIconComponent> icons = new ArrayList<>();
    private final JPanel iconContainer;
    private final JScrollPane scrollPane;

    public ReaderStatusPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.CONTENT_BG);

        // Section label with help button
        add(Theme.createSectionHeader("[ \ub9ac\ub354\uae30 \uc0c1\ud0dc ]",
            "\ub9ac\ub354\uae30 \uc0c1\ud0dc - \ub3c4\uc6c0\ub9d0",
            "\u25a0 \ub9ac\ub354\uae30 \uc0c1\ud0dc \ud328\ub110\n\n"
            + "\uac01 RFID \ub9ac\ub354\uae30\uc758 \ud604\uc7ac \uc0c1\ud0dc\ub97c \uce74\ub4dc \ud615\ud0dc\ub85c \ud45c\uc2dc\ud569\ub2c8\ub2e4.\n\n"
            + "\u25b6 \uc0c1\ud0dc \uc0c9\uc0c1\n"
            + "  \u2022 \ud68c\uc0c9: \ubbf8\uc5f0\uacb0 \uc0c1\ud0dc\n"
            + "  \u2022 \ucd08\ub85d: \uc5f0\uacb0\ub428 (\ub300\uae30 \uc911)\n"
            + "  \u2022 \ud30c\ub780: \uc778\ubca4\ud1a0\ub9ac \uc9c4\ud589 \uc911 (\ud0dc\uadf8 \uc77d\uae30 \ud65c\uc131)\n"
            + "  \u2022 \ube68\uac04: \uc5f0\uacb0 \uc624\ub958\n\n"
            + "\u25b6 \uc778\ub514\ucf00\uc774\ud130 \uc544\uc774\ucf58 (\uce74\ub4dc \uc6b0\uce21 \uc138\ub85c \ubc30\uce58)\n"
            + "  \u2022 \ud83d\udd0a \uc2a4\ud53c\ucee4 (\ube44\ud504\uc74c): \ub9ac\ub354\uae30 \ub0b4\uc7a5 \ube44\ud504\uc74c ON/OFF [\ucd08\ub85d]\n"
            + "  \u2022 \ud83d\udea8 \uacbd\uad11\ub4f1 (\uacbd\uad11\ub4f1): \ubbf8\ud5c8\uac00 \ubc18\ucd9c \uc2dc \uacbd\uad11\ub4f1 ON/OFF [\uc8fc\ud669]\n"
            + "  \u2022 \ud83d\udd14 \ubca8 (\ubd80\uc800): \ubbf8\ud5c8\uac00 \ubc18\ucd9c \uc2dc \ubd80\uc800 ON/OFF [\ud30c\ub791]\n"
            + "  \u2022 \ud68c\uc0c9 \uc544\uc774\ucf58: \ud574\ub2f9 \uae30\ub2a5 OFF \uc0c1\ud0dc\n"
            + "  \u2022 \uceec\ub7ec \uc544\uc774\ucf58: \ud574\ub2f9 \uae30\ub2a5 ON (\ub300\uae30)\n"
            + "  \u2022 \uceec\ub7ec + \uae5c\ube61\uc784: \ud65c\uc131 \ub3d9\uc791 \uc911\n\n"
            + "\u25b6 \uce74\ub4dc \uc6b0\ud074\ub9ad \uba54\ub274\n"
            + "  \u2022 \uc5f0\uacb0/\ud574\uc81c: \uac1c\ubcc4 \ub9ac\ub354\uae30 TCP \uc5f0\uacb0 \uc81c\uc5b4\n"
            + "  \u2022 \uc778\ubca4\ud1a0\ub9ac \uc2dc\uc791/\uc911\uc9c0: \ud0dc\uadf8 \uc77d\uae30 \uc81c\uc5b4\n"
            + "  \u2022 \uacbd\uad11\ub4f1/\ubd80\uc800 ON/OFF: \uc218\ub3d9 \uc81c\uc5b4\n"
            + "  \u2022 \uc548\ud14c\ub098 \uc124\uc815: \ucd9c\ub825/\ub4dc\uc6f0\ud0c0\uc784 \uc124\uc815"
        ), BorderLayout.NORTH);

        iconContainer = new JPanel(new WrapFlowLayout(FlowLayout.LEFT, Theme.CARD_GAP, Theme.CARD_GAP));
        iconContainer.setOpaque(true);
        iconContainer.setBackground(Theme.CONTENT_BG);
        scrollPane = new JScrollPane(iconContainer,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(true);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.setBackground(Theme.CONTENT_BG);
        scrollPane.getViewport().setBackground(Theme.CONTENT_BG);

        add(scrollPane, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updatePreferredHeight();
            }
        });
    }

    public void initialize(List<ReaderConfig> configs, ReaderManager manager, Runnable onConfigChanged) {
        iconContainer.removeAll();
        icons.clear();

        for (int i = 0; i < configs.size(); i++) {
            ReaderConfig cfg = configs.get(i);
            ReaderIconComponent icon = new ReaderIconComponent(i, cfg.getName(), cfg.getIp(), cfg.getPort());
            final int index = i;
            icon.setActions(
                () -> manager.connect(index),
                () -> manager.disconnect(index),
                () -> manager.startInventory(index),
                () -> manager.stopInventory(index),
                () -> manager.lightOn(index),
                () -> manager.lightOff(index),
                () -> manager.buzzerOn(index),
                () -> manager.buzzerOff(index),
                () -> ReaderIconComponent.showAntennaConfigDialog(
                    icon,
                    cfg.getAntennaPowers(), cfg.getDwellTime(),
                    powers -> {
                        manager.setAntennaConfig(index, powers);
                        if (onConfigChanged != null) onConfigChanged.run();
                    },
                    (onTime, offTime) -> {
                        manager.setDwellTime(index, onTime, offTime);
                        if (onConfigChanged != null) onConfigChanged.run();
                    }
                )
            );
            icons.add(icon);
            iconContainer.add(icon);
        }

        updatePreferredHeight();

        iconContainer.revalidate();
        iconContainer.repaint();
        revalidate();
        repaint();
    }

    private void updatePreferredHeight() {
        // Section label height (~28px) + padding
        int labelH = 28;

        if (icons.isEmpty()) {
            setPreferredSize(new Dimension(0, labelH + 4));
            return;
        }

        int hgap = Theme.CARD_GAP;
        int vgap = Theme.CARD_GAP;
        int iconW = Theme.CARD_W + hgap;
        int iconH = Theme.CARD_H + vgap;

        int availableW = getWidth();
        if (availableW <= 0 && getParent() != null) availableW = getParent().getWidth();
        if (availableW <= 0) availableW = 800;
        availableW -= 16; // insets

        int cols = Math.max(1, availableW / iconW);
        int rows = (int) Math.ceil((double) icons.size() / cols);
        int contentH = rows * iconH + vgap;
        int totalH = labelH + contentH + 4;

        int maxH = labelH + 5 * iconH + vgap + 4;
        setPreferredSize(new Dimension(0, Math.min(totalH, maxH)));

        revalidate();
    }

    public void updateStatus(int index, ReaderStatus status) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setStatus(status);
        }
    }

    public void updateLightStatus(int index, boolean lightOn) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setLightOn(lightOn);
        }
    }

    public void updateBuzzerStatus(int index, boolean buzzerOn) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setBuzzerOn(buzzerOn);
        }
    }

    public void updateConfigState(int index, boolean buzzer, boolean light, boolean beep) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setConfigState(buzzer, light, beep);
        }
    }

    public void updateBeepEnabled(int index, boolean beepEnabled) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setBeepEnabled(beepEnabled);
        }
    }

    public int getIconCount() {
        return icons.size();
    }

    private static class WrapFlowLayout extends FlowLayout {
        WrapFlowLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth <= 0) {
                    if (target.getParent() != null) {
                        targetWidth = target.getParent().getWidth();
                    }
                    if (targetWidth <= 0) targetWidth = 800;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - insets.left - insets.right;

                int x = 0;
                int y = 0;
                int rowHeight = 0;

                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + d.width > maxWidth) {
                        y += rowHeight + vgap;
                        x = 0;
                        rowHeight = 0;
                    }
                    x += d.width + hgap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                y += rowHeight;

                return new Dimension(targetWidth,
                    y + insets.top + insets.bottom + vgap * 2);
            }
        }
    }
}
