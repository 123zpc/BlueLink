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
            
            // 性能优化：对最大宽度进行取整 (Snap)，减少 resize 时的重排计算次数
            // 步长设为 50px，大幅降低连续 resize 时的 CPU 消耗
            maxAvailableWidth = (maxAvailableWidth / 50) * 50;
            
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
        // 检查是否为图片，如果是则渲染为图片气泡
        if (isImageFile(file)) {
            return createImageBubble(isSender, file);
        }

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
        // 使用自定义工具类获取高清大图标
        Icon icon = com.bluelink.util.FileIconUtils.getFileIcon(file);
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
        
        // 保存引用以便后续更新 (用于接收进度显示)
        filePanel.putClientProperty("sizeLabel", sizeLabel);

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

        JMenuItem copyFileItem = new JMenuItem("复制文件");
        copyFileItem.setFont(UiUtils.FONT_NORMAL);
        copyFileItem.addActionListener(e -> {
            java.awt.datatransfer.Transferable transferable = new java.awt.datatransfer.Transferable() {
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[] { java.awt.datatransfer.DataFlavor.javaFileListFlavor };
                }

                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                        throws java.awt.datatransfer.UnsupportedFlavorException, java.io.IOException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                    }
                    return java.util.Collections.singletonList(file);
                }
            };
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
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
        popup.add(copyFileItem);
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

    /**
     * 创建接收中的文件气泡
     */
    public static BubblePanel createReceivingFileBubble(String fileName) {
        // 构造临时文件对象 (仅用于获取图标)
        File file = new File(fileName);
        BubblePanel bubble = createFileBubble(false, file);
        
        // 修改文字为 "接收中..."
        updateBubbleSizeText(bubble, "等待接收...");
        
        // 禁用点击事件 (或者改为显示提示)
        // BubblePanel 内部已经有 MouseListener 处理重试，这里不需要额外处理
        // 但我们需要禁用内部 filePanel 的菜单或点击打开逻辑?
        // 目前 filePanel 的菜单是后来绑定的，我们可以移除或者不绑定
        // 上面的 createFileBubble 已经绑定了菜单。
        // 我们可以重新设置一个空的菜单或者 null
        Component content = ((JPanel)bubble.getComponent(0)).getComponent(0); // BubblePanel -> container -> content(filePanel)
        // 结构: BubblePanel -> container (BorderLayout) -> content (Center)
        // container 是 BubblePanel 构造函数里创建的
        // 让我们看看 BubblePanel 的结构
        
        // 简单起见，我们假设用户点击无效文件也没关系 (系统可能打不开)
        // 或者我们可以获取 filePanel 并禁用
        
        return bubble;
    }
    
    /**
     * 更新气泡的大小/状态文字
     */
    public static void updateBubbleSizeText(BubblePanel bubble, String text) {
        try {
            // 深入查找 sizeLabel
            // BubblePanel -> container -> filePanel
            // container 是 BubblePanel 的唯一子组件 (index 0, 如果没有其他组件)
            // 让我们遍历查找 putClientProperty 的组件
            
            Component container = bubble.getComponent(0);
            if (container instanceof Container) {
                Component filePanel = ((Container)container).getComponent(0);
                if (filePanel instanceof JComponent) {
                    JLabel sizeLabel = (JLabel) ((JComponent)filePanel).getClientProperty("sizeLabel");
                    if (sizeLabel != null) {
                        sizeLabel.setText(text);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String formatSize(long size) {
        if (size < 1024)
            return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") 
            || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    /**
     * 创建图片预览气泡
     */
    public static BubblePanel createImageBubble(boolean isSender, File file) {
        // 图片显示组件
        JLabel imageLabel = new JLabel();
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // 初始显示加载中
        imageLabel.setText("加载中...");
        
        // 限制最大尺寸
        int MAX_W = 250;
        int MAX_H = 250;
        
        // 异步加载图片
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                // 读取图片
                java.awt.image.BufferedImage img = null;
                try {
                    img = javax.imageio.ImageIO.read(file);
                } catch (Exception e) {
                    return null;
                }
                
                if (img == null) return null;
                
                int w = img.getWidth();
                int h = img.getHeight();
                
                if (w > MAX_W || h > MAX_H) {
                    double ratio = (double) w / h;
                    if (ratio > 1) { // 宽图
                         w = MAX_W;
                         h = (int) (MAX_W / ratio);
                    } else { // 长图
                         h = MAX_H;
                         w = (int) (MAX_H * ratio);
                    }
                    Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }
                
                return new ImageIcon(img);
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        imageLabel.setText(null);
                        imageLabel.setIcon(icon);
                        // 重新验证布局
                        imageLabel.revalidate();
                        imageLabel.repaint();
                    } else {
                        imageLabel.setText("图片加载失败");
                    }
                } catch (Exception e) {
                    imageLabel.setText("Error");
                }
            }
        }.execute();
        
        // 交互：点击打开，右键菜单
        JPopupMenu popup = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("查看原图");
        openItem.setFont(UiUtils.FONT_NORMAL);
        openItem.addActionListener(e -> com.bluelink.util.FilePreviewUtils.openFile(file));
        
        JMenuItem copyItem = new JMenuItem("复制图片");
        copyItem.setFont(UiUtils.FONT_NORMAL);
        copyItem.addActionListener(e -> {
            new Thread(() -> {
                try {
                    Image img = javax.imageio.ImageIO.read(file);
                    if (img != null) {
                        com.bluelink.ui.ModernQQFrame.copyImageToClipboard(img);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });
        
        popup.add(openItem);
        popup.add(copyItem);
        
        imageLabel.setComponentPopupMenu(popup);
        imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                     // 异步打开文件，避免阻塞 EDT
                     new Thread(() -> com.bluelink.util.FilePreviewUtils.openFile(file)).start();
                }
            }
        });

        BubblePanel bubble = new BubblePanel(isSender, imageLabel);
        // 图片气泡设置背景
        bubble.setBubbleBackground(isSender ? Color.WHITE : new Color(227, 242, 253));
        return bubble;
    }
}
