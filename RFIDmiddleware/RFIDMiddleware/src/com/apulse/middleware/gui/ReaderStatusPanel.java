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
        setBorder(BorderFactory.createTitledBorder("리더기 상태"));

        iconContainer = new JPanel(new WrapFlowLayout(FlowLayout.LEFT, 4, 4));
        scrollPane = new JScrollPane(iconContainer,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // 창 리사이즈 시 높이 재계산
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updatePreferredHeight();
            }
        });
    }

    /** 리더기 아이콘 초기화 - readers.cfg 개수만큼 동적 생성 */
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

        // 아이콘 개수에 따라 패널 높이 동적 조절
        updatePreferredHeight();

        iconContainer.revalidate();
        iconContainer.repaint();
        revalidate();
        repaint();
    }

    /** 아이콘 수와 부모 폭에 맞춰 높이 재계산 */
    private void updatePreferredHeight() {
        Insets border = getInsets();  // TitledBorder 실제 insets 사용

        if (icons.isEmpty()) {
            setPreferredSize(new Dimension(0, border.top + border.bottom));
            return;
        }

        int hgap = 4;
        int vgap = 4;
        int iconW = 90 + hgap;
        int iconH = 62 + vgap;

        int availableW = getWidth();
        if (availableW <= 0 && getParent() != null) availableW = getParent().getWidth();
        if (availableW <= 0) availableW = 800;
        availableW -= border.left + border.right;

        int cols = Math.max(1, availableW / iconW);
        int rows = (int) Math.ceil((double) icons.size() / cols);
        int contentH = rows * iconH + vgap;
        int totalH = border.top + contentH + border.bottom;

        // 최대 3행까지 표시, 그 이상은 스크롤
        int maxH = border.top + 3 * iconH + vgap + border.bottom;
        setPreferredSize(new Dimension(0, Math.min(totalH, maxH)));

        revalidate();
    }

    /** 특정 리더기 상태 업데이트 */
    public void updateStatus(int index, ReaderStatus status) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setStatus(status);
        }
    }

    /** 특정 리더기 경광등 상태 업데이트 */
    public void updateLightStatus(int index, boolean lightOn) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setLightOn(lightOn);
        }
    }

    /** 특정 리더기 부저 상태 업데이트 */
    public void updateBuzzerStatus(int index, boolean buzzerOn) {
        if (index >= 0 && index < icons.size()) {
            icons.get(index).setBuzzerOn(buzzerOn);
        }
    }

    public int getIconCount() {
        return icons.size();
    }

    /**
     * FlowLayout 변형: 컨테이너 폭에 맞춰 줄바꿈하고
     * preferred height를 자동 계산하는 레이아웃
     */
    private static class WrapFlowLayout extends FlowLayout {
        WrapFlowLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth <= 0) {
                    // 아직 크기가 결정되지 않은 경우 부모 폭 사용
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
                        // 줄바꿈
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
