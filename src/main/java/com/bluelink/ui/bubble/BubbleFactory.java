package com.bluelink.ui.bubble;

import com.bluelink.util.UiUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;

/**
 * 气泡工厂
 * 负责生成具体的聊天气泡组件
 */
public class BubbleFactory {

    /**
     * 创建文本气泡
     */
    public static BubblePanel createTextBubble(boolean isSender, String text) {
        AdaptiveTextArea textArea = new AdaptiveTextArea(text);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFont(UiUtils.FONT_NORMAL);

        if (isSender) {
            textArea.setForeground(Color.BLACK); // 发送者: 白底黑字
        } else {
            textArea.setForeground(Color.WHITE); // 接收者: 蓝底白字
        }

        // 添加右键菜单
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制");
        copyItem.setFont(UiUtils.FONT_NORMAL);
        copyItem.addActionListener(e -> {
            textArea.copy();
            // 如果不选中文本直接copy，默认copy所有 (因为设为不可编辑后可能无法获得焦点选中)
            if (textArea.getSelectedText() == null) {
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(
                        textArea.getText());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        popup.add(copyItem);

        textArea.setComponentPopupMenu(popup);

        return new BubblePanel(isSender, textArea);
    }

    /**
     * 自适应大小的文本区域
     */
    private static class AdaptiveTextArea extends JTextArea {
        public AdaptiveTextArea(String text) {
            super(text);
            setLineWrap(true);
            setWrapStyleWord(true);
        }

        @Override
        public Dimension getPreferredSize() {
            // 获取基准高度（通常是单行不可见时的偏好）
            Dimension d = super.getPreferredSize();

            // 尝试获取顶级容器 RootPane 来作为宽度的绝对参考
            Container root = getRootPane();
            int maxAvailableWidth = (root != null) ? (int) (root.getWidth() * 0.65) : 500;
            // 最小值保护 (不能太窄)
            maxAvailableWidth = Math.max(maxAvailableWidth, 200);

            // 计算该文本全部显示在一行所需的宽度
            FontMetrics fm = getFontMetrics(getFont());
            int textWidthRaw = fm.stringWidth(getText());
            Insets insets = getInsets();
            int totalTextWidth = textWidthRaw + insets.left + insets.right + 10; // extra padding

            if (totalTextWidth <= maxAvailableWidth) {
                // 如果单行能放下，就返回单行的宽度（高度通常不需变，除非 d.height 异常）
                return new Dimension(totalTextWidth, d.height);
            } else {
                // 如果需要换行，利用 View 机制精确计算高度

                // 设置一个临时 Size 触发 View 计算 (这不会影响实际布局，只影响 View 计算)
                setSize(new Dimension(maxAvailableWidth, Short.MAX_VALUE));

                int prefHeight = d.height;
                try {
                    javax.swing.text.View v = getUI().getRootView(this);
                    // 强制 View 宽度为限制宽度
                    v.setSize(maxAvailableWidth, Float.MAX_VALUE);
                    // 获取该宽度下的偏好高度
                    float h = v.getPreferredSpan(javax.swing.text.View.Y_AXIS);
                    prefHeight = (int) Math.ceil(h);
                    prefHeight += insets.top + insets.bottom;
                } catch (Exception e) {
                    // Fallback
                    int rows = (totalTextWidth / maxAvailableWidth) + 1;
                    prefHeight = rows * fm.getHeight() + insets.top + insets.bottom;
                }

                return new Dimension(maxAvailableWidth, prefHeight);
            }
        }
    }

    /**
     * 创建文件传输气泡
     */
    public static BubblePanel createFileBubble(boolean isSender, File file) {
        // 使用固定尺寸的卡片布局
        JPanel filePanel = new JPanel(new BorderLayout(10, 0)) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(216, 50);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        filePanel.setOpaque(false);
        filePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 图标区域 (左侧)
        // 使用大图标更佳，这里暂时用系统图标居中
        Icon icon = FileSystemView.getFileSystemView().getSystemIcon(file);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(40, 50)); // 左侧 40px 宽
        filePanel.add(iconLabel, BorderLayout.WEST);

        // 文本区域 (中间)
        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(file.getName());
        nameLabel.setFont(UiUtils.FONT_BOLD); // 文件名加粗
        nameLabel.setForeground(Color.BLACK); // 统一黑色文字，清晰

        JLabel sizeLabel = new JLabel(formatSize(file.length()));
        sizeLabel.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
        sizeLabel.setForeground(Color.GRAY);

        textPanel.add(nameLabel);
        textPanel.add(sizeLabel);
        filePanel.add(textPanel, BorderLayout.CENTER);

        // --- 交互逻辑 (保持不变) ---
        JPopupMenu popup = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("打开");
        openItem.setFont(UiUtils.FONT_NORMAL);
        openItem.addActionListener(e -> com.bluelink.util.FilePreviewUtils.openFile(file));

        JMenuItem openFolderItem = new JMenuItem("打开所在文件夹");
        openFolderItem.setFont(UiUtils.FONT_NORMAL);
        openFolderItem.addActionListener(e -> {
            try {
                // Windows: 使用 explorer /select 打开文件夹并选中文件
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("win")) {
                    Runtime.getRuntime().exec("explorer.exe /select," + file.getAbsolutePath());
                } else {
                    // 其他系统：降级为打开父文件夹
                    Desktop.getDesktop().open(file.getParentFile());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        JMenuItem copyNameItem = new JMenuItem("复制文件名");
        copyNameItem.setFont(UiUtils.FONT_NORMAL);
        copyNameItem.addActionListener(e -> {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(file.getName());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        });

        JMenuItem copyPathItem = new JMenuItem("复制完整路径");
        copyPathItem.setFont(UiUtils.FONT_NORMAL);
        copyPathItem.addActionListener(e -> {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(
                    file.getAbsolutePath());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        });

        popup.add(openItem);
        popup.add(openFolderItem);
        popup.addSeparator();
        popup.add(copyNameItem);
        popup.add(copyPathItem);

        java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    com.bluelink.util.FilePreviewUtils.openFile(file);
                }
            }
        };

        Component[] comps = { filePanel, iconLabel, nameLabel, sizeLabel, textPanel };
        for (Component c : comps) {
            if (c instanceof JComponent) {
                ((JComponent) c).setComponentPopupMenu(popup);
            }
            c.addMouseListener(ma);
        }

        // 创建 BubblePanel 并应用自定义颜色
        BubblePanel bubble = new BubblePanel(isSender, filePanel);

        if (isSender) {
            // 发送者: 纯白卡片 + 浅灰边框
            bubble.setBubbleBackground(Color.WHITE);
            bubble.setBubbleBorderColor(new Color(224, 224, 224));
        } else {
            // 接收者: 淡蓝卡片 + 蓝色边框
            bubble.setBubbleBackground(new Color(227, 242, 253)); // #E3F2FD
            bubble.setBubbleBorderColor(new Color(144, 202, 249)); // #90CAF9
        }

        return bubble;
    }

    /**
     * 创建代码片段气泡
     */
    public static BubblePanel createCodeBubble(boolean isSender, String code, String syntaxStyle) {
        RSyntaxTextArea textArea = new RSyntaxTextArea(code);
        textArea.setSyntaxEditingStyle(syntaxStyle != null ? syntaxStyle : SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setEditable(false);

        // 移除边框，融入气泡
        textArea.setBorder(BorderFactory.createEmptyBorder());

        // 代码区域通常比较宽，可能需要 ScrollPane，但在气泡里通常是展示性质
        // 我们可以禁用滚动条，直接展示

        return new BubblePanel(isSender, textArea);
    }

    private static String formatSize(long size) {
        if (size < 1024)
            return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}
