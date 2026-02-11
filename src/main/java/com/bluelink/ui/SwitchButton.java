package com.bluelink.ui;

import com.bluelink.util.UiUtils;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * 自定义 Switch 开关组件
 */
public class SwitchButton extends JComponent {
    private boolean selected;
    private Color switchOnColor = UiUtils.COLOR_PRIMARY;
    private Color switchOffColor = new Color(200, 200, 200);
    private Color buttonColor = Color.WHITE;
    private final int height = 24;
    private final int width = 48;
    private final int buttonGap = 2;
    private float animationState = 0; // 0.0 (off) to 1.0 (on)
    private Timer timer;
    private List<java.awt.event.ActionListener> listeners = new ArrayList<>();

    public SwitchButton(boolean selected) {
        this.selected = selected;
        this.animationState = selected ? 1.0f : 0.0f;
        setPreferredSize(new Dimension(width, height));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled()) {
                    setSelected(!SwitchButton.this.selected);
                    for (java.awt.event.ActionListener l : listeners) {
                        l.actionPerformed(new java.awt.event.ActionEvent(SwitchButton.this, java.awt.event.ActionEvent.ACTION_PERFORMED, "switch"));
                    }
                }
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            startAnimation();
        }
    }

    public void addActionListener(java.awt.event.ActionListener l) {
        listeners.add(l);
    }

    private void startAnimation() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        timer = new Timer(15, e -> {
            boolean done = false;
            if (selected) {
                animationState += 0.15f;
                if (animationState >= 1.0f) {
                    animationState = 1.0f;
                    done = true;
                }
            } else {
                animationState -= 0.15f;
                if (animationState <= 0.0f) {
                    animationState = 0.0f;
                    done = true;
                }
            }
            repaint();
            if (done) {
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        UiUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(blend(switchOffColor, switchOnColor, animationState));
        g2.fillRoundRect(0, 0, w, h, h, h);

        // Button
        int btnSize = h - buttonGap * 2;
        int btnX = buttonGap + (int) ((w - btnSize - buttonGap * 2) * animationState);
        
        g2.setColor(buttonColor);
        g2.fillOval(btnX, buttonGap, btnSize, btnSize);
    }
    
    private Color blend(Color c1, Color c2, float ratio) {
        float r = c1.getRed() + (c2.getRed() - c1.getRed()) * ratio;
        float g = c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio;
        float b = c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio;
        return new Color((int)r, (int)g, (int)b);
    }
}
