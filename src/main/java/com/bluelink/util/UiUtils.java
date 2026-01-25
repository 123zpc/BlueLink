package com.bluelink.util;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

/**
 * UI 工具类
 * 负责全局外观初始化和字体配置
 */
public class UiUtils {

    public static final Color COLOR_PRIMARY = new Color(0, 153, 255); // #0099FF
    public static final Color COLOR_BG_SIDEBAR = new Color(46, 46, 46); // 深灰侧边栏
    public static final Color COLOR_TEXT_SIDEBAR = new Color(200, 200, 200);
    public static final Font FONT_NORMAL = new Font("Microsoft YaHei", Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font("Microsoft YaHei", Font.BOLD, 14);

    /**
     * 初始化全局样式
     */
    public static void initTheme() {
        try {
            FlatLightLaf.setup();
            setGlobalFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

            // 优化滚动条样式
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            // 按钮样式调整
            UIManager.put("Button.arc", 6);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setGlobalFont(Font font) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, font);
            }
        }
    }

    /**
     * 开启抗锯齿
     */
    public static void enableAntialiasing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * 创建应用图标 (程序生成)
     */
    public static Image createAppIcon() {
        int size = 64;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        enableAntialiasing(g2);

        // 背景: 蓝色圆角矩形
        g2.setColor(COLOR_PRIMARY);
        g2.fillRoundRect(0, 0, size, size, 20, 20);

        // 图标: 白色 B (代表 BlueLink)
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 40));
        FontMetrics fm = g2.getFontMetrics();
        String text = "B";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, x, y);

        g2.dispose();
        return image;
    }

    /**
     * 创建复制图标
     * 
     * @param size  图标大小
     * @param color 图标颜色
     */
    public static Icon createCopyIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // 绘制两个重叠的矩形表示复制
                int margin = size / 6;
                int rectSize = size - margin * 2;
                int offset = size / 4;

                // 后面的矩形（虚线效果）
                g2.drawRoundRect(x + margin + offset, y + margin, rectSize - offset, rectSize - offset, 3, 3);

                // 前面的矩形（填充白色背景）
                if (c != null && c.getBackground() != null) {
                    g2.setColor(c.getBackground());
                    g2.fillRoundRect(x + margin, y + margin + offset, rectSize - offset, rectSize - offset, 3, 3);
                }
                g2.setColor(color);
                g2.drawRoundRect(x + margin, y + margin + offset, rectSize - offset, rectSize - offset, 3, 3);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * 创建对勾图标（复制成功后显示）
     */
    public static Icon createCheckIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // 绘制对勾
                int margin = size / 4;
                g2.drawLine(x + margin, y + size / 2, x + size / 2 - 1, y + size - margin - 2);
                g2.drawLine(x + size / 2 - 1, y + size - margin - 2, x + size - margin, y + margin + 2);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * 创建复制图标按钮（带动画效果）
     * 
     * @param textToCopy 要复制的文本
     */
    public static JButton createCopyButton(String textToCopy) {
        Icon copyIcon = createCopyIcon(18, COLOR_PRIMARY);
        Icon checkIcon = createCheckIcon(18, new Color(60, 160, 60));

        JButton btn = new JButton(copyIcon);
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("复制");

        btn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(textToCopy);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            btn.setIcon(checkIcon);
            btn.setToolTipText("已复制!");
            Timer t = new Timer(1200, evt -> {
                btn.setIcon(copyIcon);
                btn.setToolTipText("复制");
            });
            t.setRepeats(false);
            t.start();
        });

        // 悬停效果
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(240, 245, 255));
                btn.setOpaque(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setOpaque(false);
            }
        });

        return btn;
    }
}
