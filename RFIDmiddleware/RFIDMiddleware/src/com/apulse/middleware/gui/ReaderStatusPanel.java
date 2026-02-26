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

        // Section label instead of TitledBorder
        add(Theme.createSectionLabel("\ub9ac\ub354\uae30 \uc0c1\ud0dc"), BorderLayout.NORTH);

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

        int maxH = labelH + 3 * iconH + vgap + 4;
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
