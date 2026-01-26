package com.bluelink.ui.bubble;

import com.bluelink.util.UiUtils;
import javax.swing.*;
import java.awt.*;

/**
 * 聊天气泡面板
 * 负责绘制圆角背景和容纳内容
 */
public class BubblePanel extends JPanel {

    private final boolean isSender;
    private static final int RADIUS = 12;
    private static final int INSET_H = 12;
    private static final int INSET_V = 10;

    // 阴影参数
    private static final int SHADOW_SIZE = 2;
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 30);

    // 状态
    private boolean isFailed = false;
    private Runnable retryAction;

    public BubblePanel(boolean isSender, Component content) {
        this.isSender = isSender;
        setLayout(new BorderLayout());
        setOpaque(false); // 透明背景以便绘制自定义形状

        // 设置边距，留出阴影空间
        setBorder(BorderFactory.createEmptyBorder(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE * 2, SHADOW_SIZE * 2));

        // 内部容器用于设置实际内容的内边距
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(INSET_V, INSET_H, INSET_V, INSET_H));
        container.add(content, BorderLayout.CENTER);

        add(container, BorderLayout.CENTER);

        // 添加鼠标监听处理重试点击
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (isFailed && isSender && retryAction != null) {
                    System.out.println("DEBUG: Mouse clicked at " + e.getX() + ", failed=" + isFailed);
                    // 现在的边距是 2+22 = 24，所以 icon 在左侧。
                    // 简单放宽判断
                    if (e.getX() < 40) {
                        System.out.println("DEBUG: Executing retry action!");
                        retryAction.run();
                    } else {
                        System.out.println("DEBUG: Click ignored (X=" + e.getX() + ")");
                    }
                }
            }
        });
    }

    public void setRetryAction(Runnable retryAction) {
        System.out.println("DEBUG: setRetryAction called");
        this.retryAction = retryAction;
    }

    // 自定义颜色
    private Color customBackground;
    private Color customBorderColor;

    public void setBubbleBackground(Color color) {
        this.customBackground = color;
        repaint();
    }

    public void setBubbleBorderColor(Color color) {
        this.customBorderColor = color;
        repaint();
    }

    // 进度条状态 (-1 表示不显示)
    private float progress = -1f;

    public void setProgress(float progress) {
        this.progress = progress;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        UiUtils.enableAntialiasing(g2);

        // 计算偏移量：如果失败且是发送者，背景向右移，留出左边画图标
        int offset = (isFailed && isSender) ? 22 : 0;

        int w = getWidth() - SHADOW_SIZE * 2 - offset;
        int h = getHeight() - SHADOW_SIZE * 2;
        int x = SHADOW_SIZE + offset;
        int y = SHADOW_SIZE;

        // 绘制阴影
        g2.setColor(SHADOW_COLOR);
        g2.fillRoundRect(x + 1, y + 1, w, h, RADIUS, RADIUS);

        // 绘制背景
        if (customBackground != null) {
            g2.setColor(customBackground);
        } else if (isSender) {
            g2.setColor(Color.WHITE);
        } else {
            g2.setColor(UiUtils.COLOR_PRIMARY);
        }
        g2.fillRoundRect(x, y, w, h, RADIUS, RADIUS);

        // 绘制进度条
        if (progress >= 0 && progress <= 1.0f) {
            // 使用裁剪确保进度条不超出圆角
            Shape originalClip = g2.getClip();
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, RADIUS, RADIUS));
            
            // 进度条颜色 (半透明白色或强调色)
            if (isSender) {
                g2.setColor(new Color(46, 204, 113, 180)); // Green on White
            } else {
                g2.setColor(new Color(255, 255, 255, 128)); // White on Blue
            }
            
            // 底部进度条模式
            // int barHeight = 4;
            // g2.fillRect(x, y + h - barHeight, (int)(w * progress), barHeight);
            
            // 全背景覆盖模式 (类似传输遮罩)
            // 从左到右填充
            g2.fillRect(x, y, (int)(w * progress), h);
            
            g2.setClip(originalClip);
        }

        // 绘制边框
        if (customBorderColor != null) {
            g2.setColor(customBorderColor);
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
        } else if (isSender) {
            g2.setColor(new Color(220, 220, 220)); // 浅灰色边框
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
        }

        // 绘制失败图标 (红色感叹号)
        if (isFailed && isSender) {
            int iconSize = 16;
            // 图标画在左侧空出来的区域
            int iconX = SHADOW_SIZE; // 基本贴左边 (2px)
            // 垂直居中
            int iconY = y + (h - iconSize) / 2;

            // 红色圆
            g2.setColor(new Color(220, 60, 60));
            g2.fillOval(iconX, iconY, iconSize, iconSize);

            // 白色感叹号
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            String ex = "!";
            int exX = iconX + (iconSize - fm.stringWidth(ex)) / 2;
            int exY = iconY + ((iconSize - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(ex, exX, exY);
        }
    }

    public void setStatus(boolean isFailed) {
        System.out.println("DEBUG: setStatus " + isFailed);
        this.isFailed = isFailed;

        // 动态调整边框，为图标留出空间
        if (isFailed && isSender) {
            setBorder(BorderFactory.createEmptyBorder(SHADOW_SIZE, SHADOW_SIZE + 22, SHADOW_SIZE * 2, SHADOW_SIZE * 2));
        } else {
            setBorder(BorderFactory.createEmptyBorder(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE * 2, SHADOW_SIZE * 2));
        }

        revalidate();
        repaint();

        // 如果失败，尝试将内部组件文字变红
        if (isFailed) {
            updateTextColor(this, new Color(220, 60, 60));
        } else {
            // 恢复颜色 (简单处理，假设发送者是黑色)
            updateTextColor(this, Color.BLACK);
        }
    }

    private void updateTextColor(Container c, Color color) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof javax.swing.text.JTextComponent) {
                comp.setForeground(color);
            } else if (comp instanceof JLabel) {
                comp.setForeground(color);
            } else if (comp instanceof Container) {
                updateTextColor((Container) comp, color);
            }
        }
    }
}
