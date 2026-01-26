package com.bluelink.ui;

import com.bluelink.util.MdCodeUtil;
import com.bluelink.util.UiUtils;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;

/**
 * 主窗口框架
 * 模仿 QQ NT 风格布局
 */
public class ModernQQFrame extends JFrame {

    // CardLayout 页面管理
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private ConnectionPanel connectionPanel;
    private static final String PAGE_CONNECTION = "connection";
    private static final String PAGE_CHAT = "chat";

    private JPanel rootPanel;
    private JPanel sidebarPanel;
    private JPanel contentPanel;

    // 右侧组件
    private JLabel headerLabel;
    private JPanel chatArea;
    private JScrollPane chatScrollPane; // 保存 scrollPane 引用
    private JTextArea inputArea; // 暂时用 JTextArea，后续升级
    private JButton sendButton;
    private TrayManager trayManager;

    // 悬浮层组件
    private JLayeredPane layeredChatPane;
    private JButton backToBottomBtn;

    // 状态记录
    private long minLoadedId = Long.MAX_VALUE; // 当前加载到的最小 ID
    private boolean isUserAtBottom = true; // 用户是否在底部
    private boolean isLoadingHistory = false; // 是否正在加载历史消息
    private boolean hasLoadedAllHistory = false; // 是否已加载全部历史

    // 发送模式: true = 回车发送, false = Ctrl+回车发送
    private boolean enterToSend = true;

    // 网络组件
    private com.bluelink.net.BluetoothServer server;
    private com.bluelink.net.BluetoothClient client;
    private com.bluelink.net.BluetoothSession currentSession;

    public ModernQQFrame() {
        initUI();
        initNetwork();
        trayManager = new TrayManager(this);
    }

    private void initNetwork() {
        // 1. 启动服务端
        server = new com.bluelink.net.BluetoothServer();
        server.setListener(new TransferListenerImpl());
        server.start();

        // 2. 初始化客户端
        client = new com.bluelink.net.BluetoothClient();
        client.setListener(new TransferListenerImpl());
    }

    // 网络事件处理
    private class TransferListenerImpl implements com.bluelink.net.TransferListener {
        @Override
        public void onMessageReceived(String sender, String content) {
            SwingUtilities.invokeLater(() -> {
                addMessage(false, content);
                trayManager.showNotification("收到新消息", content);
            });
        }

        @Override
        public void onFileReceived(String sender, File file) {
            SwingUtilities.invokeLater(() -> {
                addFileMessage(false, file);
                trayManager.showNotification("收到文件", file.getName());
            });
        }

        @Override
        public void onTransferProgress(String fileName, long current, long total) {
            // 暂不实现进度条
        }

        @Override
        public void onConnectionStatusChanged(boolean isConnected, String deviceName) {
            SwingUtilities.invokeLater(() -> {
                headerLabel.setText(isConnected ? "已连接: " + deviceName : "未连接");
                // 连接成功时自动切换到聊天页面并发送通知
                if (isConnected) {
                    showChatPage();
                    // 发送系统通知
                    trayManager.showNotification("连接成功", "已与 " + deviceName + " 建立连接");
                    // 通知连接面板
                    if (connectionPanel != null) {
                        connectionPanel.onIncomingConnection(deviceName);
                    }
                }
            });
        }

        @Override
        public void onSessionCreated(com.bluelink.net.BluetoothSession session) {
            currentSession = session;
            System.out.println("[UI] 会话已建立，保存 session");
        }

        @Override
        public void onError(String message) {
            SwingUtilities.invokeLater(() -> {
                System.err.println("错误: " + message);
                trayManager.showNotification("错误", message);
                
                // 如果在连接页面，显示错误
                if (connectionPanel != null && connectionPanel.isVisible()) {
                    connectionPanel.onConnectionFailed(message);
                }
            });
        }
    }

    private void initUI() {
        setTitle("BlueLink");
        setSize(900, 600);
        setMinimumSize(new Dimension(900, 600)); // 限制最小窗口大小
        setIconImage(UiUtils.createAppIcon()); // 设置应用图标
        setLocationRelativeTo(null);

        // 点击关闭时隐藏窗口 (最小化到托盘)
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                setVisible(false);
            }
        });

        // === CardLayout 管理页面切换 ===
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // 1. 连接页面
        connectionPanel = new ConnectionPanel(new ConnectionPanel.ConnectionCallback() {
            @Override
            public void onConnected(boolean isHost, String peerAddress) {
                if (isHost) {
                    // 等待方：不切换页面，等待真正的连接通知（由 onConnectionStatusChanged 处理）
                    // 服务端已在后台运行
                } else if (peerAddress != null) {
                    // 连接方：发起连接，连接成功后由 onConnectionStatusChanged 自动切换页面
                    client.connect(peerAddress);
                }
            }

            @Override
            public void onDevModeEnter() {
                // 开发模式直接进入
                showChatPage();
            }

            @Override
            public void onSkip() {
                // 跳过连接，进入聊天界面（离线模式或从设置返回）
                showChatPage();
            }
        });
        cardPanel.add(connectionPanel, PAGE_CONNECTION);

        // 2. 聊天页面
        rootPanel = new JPanel(new MigLayout("insets 0, gap 0, fill", "[70px!, fill][grow, fill]", "[grow, fill]"));

        // 2.1 左侧边栏
        createSidebar();
        rootPanel.add(sidebarPanel, "cell 0 0");

        // 2.2 右侧内容区
        createContentPanel();
        rootPanel.add(contentPanel, "cell 1 0");

        cardPanel.add(rootPanel, PAGE_CHAT);

        // 设置 cardPanel 为主内容
        setContentPane(cardPanel);

        // === 默认显示连接页面 ===
        cardLayout.show(cardPanel, PAGE_CONNECTION);

        // --- 绑定事件 ---
        sendButton.addActionListener(e -> performSend());

        // 输入框键盘事件：处理回车发送
        setupInputKeyBindings();

        // 输入框右键菜单：切换发送模式
        setupInputContextMenu();

        // --- 拖拽支持 ---
        enableDragAndDrop();
    }

    /**
     * 切换到聊天页面
     */
    private void showChatPage() {
        cardLayout.show(cardPanel, PAGE_CHAT);
    }

    /**
     * 切换到连接页面
     */
    private void showConnectionPage() {
        showConnectionPage(false);
    }

    /**
     * 切换到连接页面
     * 
     * @param fromSettings true=从设置页来，显示"返回"按钮；false=初次启动
     */
    private void showConnectionPage(boolean fromSettings) {
        connectionPanel.setFromSettings(fromSettings);
        cardLayout.show(cardPanel, PAGE_CONNECTION);
    }

    /**
     * 设置输入框的键盘绑定
     * 根据 enterToSend 模式：
     * - true: 回车发送，Ctrl+回车换行
     * - false: Ctrl+回车发送，回车换行
     */
    private void setupInputKeyBindings() {
        // 移除默认的回车行为
        inputArea.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                "sendOrNewline");
        inputArea.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "ctrlEnterAction");

        inputArea.getActionMap().put("sendOrNewline", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (enterToSend) {
                    // 回车 = 发送
                    performSend();
                } else {
                    // 回车 = 换行
                    inputArea.insert("\n", inputArea.getCaretPosition());
                }
            }
        });

        inputArea.getActionMap().put("ctrlEnterAction", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (enterToSend) {
                    // Ctrl+回车 = 换行
                    inputArea.insert("\n", inputArea.getCaretPosition());
                } else {
                    // Ctrl+回车 = 发送
                    performSend();
                }
            }
        });
    }

    /**
     * 设置输入框右键菜单，用于切换发送模式
     */
    private void setupInputContextMenu() {
        JPopupMenu popup = new JPopupMenu();
        JCheckBoxMenuItem enterSendItem = new JCheckBoxMenuItem("回车发送 (Ctrl+回车换行)", enterToSend);

        enterSendItem.addActionListener(e -> {
            enterToSend = enterSendItem.isSelected();
            String tip = enterToSend ? "当前模式: 回车发送" : "当前模式: Ctrl+回车发送";
            inputArea.setToolTipText(tip);
        });

        popup.add(enterSendItem);
        inputArea.setComponentPopupMenu(popup);

        // 初始提示
        inputArea.setToolTipText("当前模式: 回车发送");
    }

    private void enableDragAndDrop() {
        new java.awt.dnd.DropTarget(this, new java.awt.dnd.DropTargetAdapter() {
            @SuppressWarnings("unchecked")
            @Override
            public void drop(java.awt.dnd.DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    java.util.List<File> droppedFiles = (java.util.List<File>) dtde.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);

                    for (File file : droppedFiles) {
                        // 使用新的发送逻辑 (支持状态持久化和重试)
                        performFileSend(file);
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void performSend() {
        String text = inputArea.getText().trim();
        if (text.isEmpty())
            return;

        if (text.startsWith("/connect ")) {
            String addr = text.substring(9).trim();
            client.connect(addr);
            inputArea.setText("");
            return;
        }

        // 1. 初始化并保存 (status=SENDING) -> 获取 ID
        com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("TEXT", true, text, 0);
        item.status = "SENDING";
        com.bluelink.db.TransferDao.save(item);

        // 2. Optimistic UI
        com.bluelink.ui.bubble.BubblePanel bubble = renderTextBubble(true, text);
        inputArea.setText(""); // 立即清空输入框

        // 强制滚到底部
        scrollToBottom();

        // 3. 异步网络发送
        new Thread(() -> {
            try {
                if (currentSession != null) {
                    currentSession.sendMessage(text);
                } else {
                    client.send(text);
                }

                // 发送成功: 更新数据库状态 (Status=SUCCESS)
                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "SUCCESS");
                }

            } catch (Exception e) {
                // 发送失败: 更新 UI 和 数据库
                SwingUtilities.invokeLater(() -> {
                    if (bubble != null) {
                        bubble.setStatus(true); // 显示失败红点
                        // 绑定重试 action
                        bubble.setRetryAction(() -> performResend(item, bubble));
                    }
                });

                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "FAILED");
                }
            }
        }).start();
    }

    private void performFileSend(File file) {
        // 1. 初始化并保存 (status=SENDING) -> 获取 ID
        com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("FILE", true,
                file.getAbsolutePath(), file.length());
        item.status = "SENDING";
        com.bluelink.db.TransferDao.save(item);

        // 2. Optimistic UI
        com.bluelink.ui.bubble.BubblePanel bubble = renderFileBubble(true, file);

        // 强制滚到底部
        scrollToBottom();

        // 3. 异步网络发送
        new Thread(() -> {
            try {
                if (currentSession != null) {
                    currentSession.sendFile(file);
                } else {
                    client.sendFile(file);
                }

                // 发送成功: 更新数据库状态 (Status=SUCCESS)
                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "SUCCESS");
                }

            } catch (Exception e) {
                // 发送失败: 更新 UI 和 数据库
                SwingUtilities.invokeLater(() -> {
                    if (bubble != null) {
                        bubble.setStatus(true); // 显示失败红点
                        // 绑定重试 action
                        bubble.setRetryAction(() -> performResend(item, bubble));
                    }
                });

                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "FAILED");
                }
            }
        }).start();
    }

    private void createSidebar() {
        sidebarPanel = new JPanel(new MigLayout("insets 10, flowy, alignx center", "[center]", "[]20[]push"));
        sidebarPanel.setBackground(UiUtils.COLOR_BG_SIDEBAR);

        // 头像组件
        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                UiUtils.enableAntialiasing(g);
                Graphics2D g2 = (Graphics2D) g;

                // 绘制圆形背景
                g2.setColor(new Color(100, 100, 100)); // 占位颜色
                Shape circle = new Ellipse2D.Double(0, 0, getWidth(), getHeight());
                g2.fill(circle);

                // 绘制首字母
                g2.setColor(Color.WHITE);
                g2.setFont(UiUtils.FONT_BOLD.deriveFont(16f));
                FontMetrics fm = g2.getFontMetrics();
                String text = "我";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(text, x, y);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(40, 40);
            }
        };

        // 头像悬停效果
        avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        avatar.setToolTipText("点击打开设置");

        // 头像点击事件
        avatar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showSettingsDialog();
            }
        });

        // 本机码
        String myCode = MdCodeUtil.getMyCode();
        JLabel codeLabel = new JLabel("<html><center>" + myCode + "</center></html>");
        codeLabel.setForeground(UiUtils.COLOR_TEXT_SIDEBAR);
        codeLabel.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));

        sidebarPanel.add(avatar);
        sidebarPanel.add(codeLabel);
    }

    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "设置", true);
        dialog.setUndecorated(true);
        dialog.setSize(520, 480); // Increased width for tabs
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0, 0, 0, 0));

        // Temp State for Safe Settings
        java.util.concurrent.atomic.AtomicBoolean tempEnterToSend = new java.util.concurrent.atomic.AtomicBoolean(
                this.enterToSend);
        java.util.concurrent.atomic.AtomicInteger tempTimeout = new java.util.concurrent.atomic.AtomicInteger(
                com.bluelink.util.AppConfig.getConnectionTimeoutSeconds());
        java.util.concurrent.atomic.AtomicReference<String> tempPath = new java.util.concurrent.atomic.AtomicReference<>(
                com.bluelink.util.AppConfig.getDownloadPath());

        // Save Button (Declared early for access in listeners)
        JButton saveBtn = new JButton("保存设置");
        saveBtn.setBackground(UiUtils.COLOR_PRIMARY);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(UiUtils.FONT_NORMAL);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);
        saveBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        saveBtn.setPreferredSize(new Dimension(100, 30));
        saveBtn.setEnabled(false); // Initially disabled

        // Change Detection Logic
        Runnable checkChanges = () -> {
            boolean changed = (tempEnterToSend.get() != this.enterToSend)
                    || (tempTimeout.get() != com.bluelink.util.AppConfig.getConnectionTimeoutSeconds())
                    || (!tempPath.get().equals(com.bluelink.util.AppConfig.getDownloadPath()));

            saveBtn.setEnabled(changed);
            if (changed) {
                saveBtn.setText("保存设置*");
                saveBtn.setBackground(UiUtils.COLOR_PRIMARY);
            } else {
                saveBtn.setText("保存设置");
                saveBtn.setBackground(new Color(180, 190, 200)); // Disabled look color
            }
        };
        // Initial check to set button state (should be disabled)
        checkChanges.run();

        // Close Logic
        Runnable closeAction = () -> {
            boolean changed = saveBtn.isEnabled();
            if (changed) {
                int opt = JOptionPane.showConfirmDialog(dialog, "设置未保存，确定退出吗？", "未保存退出", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (opt == JOptionPane.YES_OPTION) {
                    dialog.dispose();
                }
            } else {
                dialog.dispose();
            }
        };

        // Save Logic
        saveBtn.addActionListener(e -> {
            this.enterToSend = tempEnterToSend.get();
            com.bluelink.util.AppConfig.setConnectionTimeout(tempTimeout.get());
            com.bluelink.util.AppConfig.setDownloadPath(tempPath.get());

            // Update Main UI based on changes if needed
            inputArea.setToolTipText(this.enterToSend ? "模式: Enter 发送" : "模式: Ctrl+Enter 发送");

            dialog.dispose();
        });

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                UiUtils.enableAntialiasing(g2);

                int shadowSize = 5;
                int w = getWidth() - shadowSize * 2;
                int h = getHeight() - shadowSize * 2;
                int arc = 20;

                // 绘制阴影
                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(shadowSize + 2, shadowSize + 2, w, h, arc, arc);

                // 绘制背景
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(shadowSize, shadowSize, w, h, arc, arc);

                // 绘制边框
                g2.setColor(new Color(230, 230, 230));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(shadowSize, shadowSize, w, h, arc, arc);
            }
        };
        // Use BorderLayout for main structure: Title (North), ConnectCard, Tabs
        // (Center), Footer (South)
        // Equalize gap: Title-Card (10px), Card-Tabs (10px)
        mainPanel.setLayout(new MigLayout("insets 20 25 10 25, fill, wrap 1", "[grow]", "[]10[]10[grow]0[]"));
        mainPanel.setOpaque(false);

        // --- 1. 自定义标题栏 (含拖动和关闭) ---
        JPanel titlePanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("应用设置");
        titleLabel.setFont(UiUtils.FONT_BOLD.deriveFont(18f));

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Arial", Font.BOLD, 20));
        closeBtn.setForeground(Color.GRAY);
        closeBtn.setBorder(null);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closeAction.run()); // Use Safe Close

        titlePanel.add(titleLabel);
        titlePanel.add(closeBtn);
        mainPanel.add(titlePanel, "growx, gapbottom 0");

        // 窗口拖动逻辑
        java.awt.event.MouseAdapter dragListener = new java.awt.event.MouseAdapter() {
            int pX, pY;

            public void mousePressed(java.awt.event.MouseEvent e) {
                pX = e.getX();
                pY = e.getY();
            }

            public void mouseDragged(java.awt.event.MouseEvent e) {
                dialog.setLocation(dialog.getLocation().x + e.getX() - pX, dialog.getLocation().y + e.getY() - pY);
            }
        };
        mainPanel.addMouseListener(dragListener);
        mainPanel.addMouseMotionListener(dragListener);

        // --- 2. 顶部：本机信息 ---
        // 将本机码信息放在 Tabs 上方，作为公共信息展示
        JPanel codeCard = new JPanel(new MigLayout("insets 10, fillx, wrap 1", "[center]"));
        codeCard.setBackground(new Color(248, 250, 255));
        codeCard.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 1, true));

        String myCode = MdCodeUtil.getMyCode();
        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        codeRow.setOpaque(false);

        JLabel codeVal = new JLabel(myCode);
        codeVal.setFont(new Font("Consolas", Font.BOLD, 22));
        codeVal.setForeground(new Color(50, 50, 50));

        JButton copyBtn = UiUtils.createCopyButton(myCode);

        codeRow.add(new JLabel("本机 ID: "));
        codeRow.add(codeVal);
        codeRow.add(copyBtn);

        codeCard.add(new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                UiUtils.enableAntialiasing(g2);
                g2.setColor(UiUtils.COLOR_PRIMARY);
                g2.fillOval(0, 0, 40, 40);
                g2.setColor(Color.WHITE);
                g2.setFont(UiUtils.FONT_BOLD.deriveFont(16f));
                String s = "我";
                FontMetrics fm = g2.getFontMetrics();
                int x = (40 - fm.stringWidth(s)) / 2;
                int y = (40 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, x, y);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(40, 40);
            }
        }, "split 2, gapright 10");

        codeCard.add(codeRow);
        mainPanel.add(codeCard, "growx, h 65!, gapbottom 0");

        // --- 3. 垂直 Tabs 内容区 ---
        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.setFocusable(false);
        tabbedPane.setFont(UiUtils.FONT_NORMAL.deriveFont(14f));
        tabbedPane.setBackground(Color.WHITE);
        tabbedPane.setOpaque(true);

        // SVG Icons
        FlatSVGIcon iconSend = new FlatSVGIcon("com/bluelink/ui/icons/send.svg", 16, 16);
        FlatSVGIcon iconSettings = new FlatSVGIcon("com/bluelink/ui/icons/settings.svg", 16, 16);
        FlatSVGIcon iconConnect = new FlatSVGIcon("com/bluelink/ui/icons/connect.svg", 16, 16);

        // Color filter for selected state (Blue)
        FlatSVGIcon.ColorFilter blueFilter = new FlatSVGIcon.ColorFilter(color -> UiUtils.COLOR_PRIMARY);

        tabbedPane.addChangeListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                String title = tabbedPane.getTitleAt(i);
                if (title.startsWith("<html>")) {
                    title = title.replaceAll("<html>.*<font color=.*>(.*)</font>.*</html>", "$1");
                }

                // Reset icon color
                Icon currentIcon = tabbedPane.getIconAt(i);
                if (currentIcon instanceof FlatSVGIcon) {
                    ((FlatSVGIcon) currentIcon).setColorFilter(null);
                }

                if (i == selected) {
                    tabbedPane.setTitleAt(i, "<html><font color='#0099FF'>" + title + "</font></html>");
                    if (currentIcon instanceof FlatSVGIcon) {
                        ((FlatSVGIcon) currentIcon).setColorFilter(blueFilter);
                    }
                } else {
                    tabbedPane.setTitleAt(i, title);
                }
            }
            tabbedPane.repaint(); // Ensure icon repaint
        });

        // Tab 1: 发送设置
        JPanel settingPanel = new JPanel(new MigLayout("insets 15, fillx, wrap 1", "[grow]"));
        settingPanel.setOpaque(false);

        JLabel setTip = new JLabel("发送快捷键设置");
        setTip.setFont(UiUtils.FONT_BOLD);
        settingPanel.add(setTip, "gaptop 5, gapbottom 10");

        JRadioButton rb1 = new JRadioButton("Enter 发送", tempEnterToSend.get());
        JRadioButton rb2 = new JRadioButton("Ctrl + Enter 发送", !tempEnterToSend.get());

        Font radioFont = UiUtils.FONT_NORMAL.deriveFont(14f);
        rb1.setFont(radioFont);
        rb2.setFont(radioFont);
        rb1.setOpaque(false);
        rb2.setOpaque(false);
        rb1.setFocusPainted(false);
        rb2.setFocusPainted(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rb1);
        bg.add(rb2);

        rb1.addActionListener(e -> {
            tempEnterToSend.set(true);
            checkChanges.run();
        });
        rb2.addActionListener(e -> {
            tempEnterToSend.set(false);
            checkChanges.run();
        });

        settingPanel.add(rb1);
        settingPanel.add(rb2, "gaptop 5");

        // Tab 2: 通用设置
        JPanel generalPanel = new JPanel(new MigLayout("insets 15, fillx, wrap 1", "[grow]"));
        generalPanel.setOpaque(false);

        JLabel genTip = new JLabel("通用功能设置");
        genTip.setFont(UiUtils.FONT_BOLD);
        generalPanel.add(genTip, "gaptop 5, gapbottom 10");

        // 连接超时
        JPanel timeoutPanel = new JPanel(new MigLayout("insets 0, fillx", "[][grow]"));
        timeoutPanel.setOpaque(false);
        JLabel timeoutLabel = new JLabel("连接超时 (秒):");
        JTextField timeoutField = new JTextField(String.valueOf(tempTimeout.get()));

        timeoutPanel.add(timeoutLabel);
        timeoutPanel.add(timeoutField, "width 60!");

        // Listener for Timeout Field
        javax.swing.event.DocumentListener timeoutListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            void update() {
                try {
                    String txt = timeoutField.getText().trim();
                    if (!txt.isEmpty()) {
                        int val = Integer.parseInt(txt);
                        tempTimeout.set(val);
                    }
                } catch (NumberFormatException ex) {
                }
                checkChanges.run();
            }
        };
        timeoutField.getDocument().addDocumentListener(timeoutListener);

        generalPanel.add(timeoutPanel);

        // 下载路径
        JLabel pathLabel = new JLabel("文件保存位置:");
        generalPanel.add(pathLabel, "gaptop 15");

        JPanel pathRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        pathRow.setOpaque(false);
        JTextField pathField = new JTextField(tempPath.get());
        pathField.setEditable(false);
        JButton changePathBtn = new JButton("浏览...");
        changePathBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        changePathBtn.addActionListener(e -> {
            try {
                javax.swing.LookAndFeel currentLaf = javax.swing.UIManager.getLookAndFeel();
                javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());

                JFileChooser chooser = new JFileChooser();
                chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择文件保存位置");

                File currentDir = new File(pathField.getText());
                if (!currentDir.exists())
                    currentDir.mkdirs();
                if (!currentDir.exists())
                    currentDir = new File(System.getProperty("user.home"));
                chooser.setCurrentDirectory(currentDir);

                int result = chooser.showOpenDialog(dialog);
                javax.swing.UIManager.setLookAndFeel(currentLaf);

                if (result == JFileChooser.APPROVE_OPTION) {
                    String path = chooser.getSelectedFile().getAbsolutePath();
                    pathField.setText(path);
                    tempPath.set(path); // Update temp
                    checkChanges.run();
                }
            } catch (Throwable t) {
                t.printStackTrace();
                String newPath = javax.swing.JOptionPane.showInputDialog(dialog, "请输入保存路径:", pathField.getText());
                if (newPath != null && !newPath.trim().isEmpty()) {
                    File dir = new File(newPath.trim());
                    if (!dir.exists())
                        dir.mkdirs();
                    pathField.setText(dir.getAbsolutePath());
                    tempPath.set(dir.getAbsolutePath()); // Update temp
                    checkChanges.run();
                }
            }
        });

        pathRow.add(pathField, "growx");
        pathRow.add(changePathBtn);
        generalPanel.add(pathRow, "gaptop 5");

        // Tab 3: 连接设备
        JPanel connectPanel = new JPanel(new MigLayout("insets 15, fillx, wrap 1", "[center]"));
        connectPanel.setOpaque(false);

        JLabel connTip = new JLabel("添加新设备连接");
        connTip.setFont(UiUtils.FONT_BOLD);
        connectPanel.add(connTip, "align left, gaptop 5, gapbottom 20");

        JButton gotoConnectBtn = new JButton("前往连接页面");
        gotoConnectBtn.setBackground(UiUtils.COLOR_PRIMARY);
        gotoConnectBtn.setForeground(Color.WHITE);
        gotoConnectBtn.setFont(UiUtils.FONT_NORMAL);
        gotoConnectBtn.setFocusPainted(false);
        gotoConnectBtn.setBorderPainted(false);
        gotoConnectBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gotoConnectBtn.setPreferredSize(new Dimension(180, 40));

        gotoConnectBtn.addActionListener(e -> {
            // Check changes before leaving
            if (saveBtn.isEnabled()) {
                int opt = JOptionPane.showConfirmDialog(dialog, "设置未保存，确定离开吗？", "未保存警告", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (opt != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            dialog.dispose();
            showConnectionPage(true);
        });

        connectPanel.add(gotoConnectBtn, "gaptop 20");

        // Tab 4: 关于软件
        JPanel aboutPanel = new JPanel(new MigLayout("insets 15, fillx, wrap 1", "[grow]"));
        aboutPanel.setOpaque(false);

        JLabel aboutTitle = new JLabel("关于 BlueLink");
        aboutTitle.setFont(UiUtils.FONT_BOLD.deriveFont(16f));
        aboutPanel.add(aboutTitle, "gapbottom 10");

        // Author
        JPanel authorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        authorRow.setOpaque(false);
        authorRow.add(new JLabel("作者: "));
        JLabel authorLabel = new JLabel("ZPC");
        authorLabel.setFont(UiUtils.FONT_BOLD);
        authorRow.add(authorLabel);
        aboutPanel.add(authorRow, "gaptop 5");

        // Email
        JPanel emailRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        emailRow.setOpaque(false);
        emailRow.add(new JLabel("联系邮箱: "));
        JTextField emailField = new JTextField("privacyporton@proton.me");
        emailField.setEditable(false);
        emailField.setBorder(null);
        emailField.setOpaque(false);
        emailField.setFont(UiUtils.FONT_NORMAL);
        emailRow.add(emailField);
        aboutPanel.add(emailRow, "gaptop 5");

        // GitHub
        JPanel githubRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        githubRow.setOpaque(false);
        githubRow.add(new JLabel("开源地址: "));
        JLabel githubLink = new JLabel("<html><a href=''>https://github.com/123zpc/BlueLink</a></html>");
        githubLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        githubLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/123zpc/BlueLink"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        githubRow.add(githubLink);
        aboutPanel.add(githubRow, "gaptop 5");

        // Version/Date
        aboutPanel.add(new JLabel("版本: 1.0.0 (2026-01-25)"), "gaptop 20");

        // 添加 Tabs
        try {
            FlatSVGIcon iconAbout = new FlatSVGIcon("com/bluelink/ui/icons/about.svg", 16, 16);

            tabbedPane.addTab("发送", iconSend, settingPanel);
            tabbedPane.addTab("通用", iconSettings, generalPanel);
            tabbedPane.addTab("连接", iconConnect, connectPanel);
            tabbedPane.addTab("关于", iconAbout, aboutPanel);
        } catch (Exception e) {
            // Fallback if icons fail
            tabbedPane.addTab("发送", settingPanel);
            tabbedPane.addTab("通用", generalPanel);
            tabbedPane.addTab("连接", connectPanel);
            tabbedPane.addTab("关于", aboutPanel);
        }

        mainPanel.add(tabbedPane, "grow");

        // --- 4. 底部操作 ---
        JPanel bottomPanel = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
        bottomPanel.setOpaque(false);

        JButton clearBtn = new JButton("清空所有记录");
        clearBtn.setForeground(new Color(220, 60, 60));
        clearBtn.setFont(UiUtils.FONT_NORMAL);
        clearBtn.setBorderPainted(false);
        clearBtn.setContentAreaFilled(false);
        clearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            int opt = JOptionPane.showConfirmDialog(dialog, "确定清空？不可恢复。", "确认", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                clearChatHistory();
                // Check changes before dispose inside clearChatHistory? No, just clear DB/UI.
                // But user might want to continue settings.
                // Re-enabling dialog.dispose() in clear button might act as "Cancel" for
                // settings changes?
                // Standard behavior: Clear action is independent.
                // Previous code disposed dialog. I will keep it but warn if settings changed?
                // "清空" is unrelated to "保存设置". But closing the dialog abandons settings
                // changes.
                // Ideally clear history shouldn't close dialog, but if it does, we should
                // prompt safe close.
                // Let's assume clear history closes dialog.
                if (saveBtn.isEnabled()) {
                    // Unsaved changes will be lost check
                    // The clear confirmation is already there.
                    // Let's just run closeAction() after clearing?
                    // Or just dispose.
                    // Simple approach: Dispose effectively cancels settings changes.
                    dialog.dispose();
                } else {
                    dialog.dispose();
                }
            }
        });

        bottomPanel.add(clearBtn); // Left
        bottomPanel.add(saveBtn); // Right

        mainPanel.add(bottomPanel, "growx");

        // Force trigger styling for the first tab
        if (tabbedPane.getTabCount() > 0) {
            tabbedPane.setSelectedIndex(-1);
            tabbedPane.setSelectedIndex(0);
        }

        dialog.setContentPane(mainPanel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); // Safe Close
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeAction.run();
            }
        });

        dialog.setVisible(true);
    }

    /**
     * 清空聊天记录
     */
    private void clearChatHistory() {
        // 清除 UI
        chatArea.removeAll();
        chatArea.revalidate();
        chatArea.repaint();

        // 清除数据库
        com.bluelink.db.TransferDao.clearAll();
    }

    private void createContentPanel() {
        // 右侧布局: Header (Top), Chat (Center), Input (Bottom)
        contentPanel = new JPanel(
                new MigLayout("insets 0, fill, wrap 1", "[grow, fill]", "[40px!, fill][grow, fill][150px!, fill]"));
        contentPanel.setBackground(Color.WHITE);

        // 2.1 Header
        JPanel headerPanel = new JPanel(new MigLayout("insets 0 20 0 20, fill"));
        headerPanel.setBackground(new Color(245, 245, 245));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)));

        headerLabel = new JLabel("未连接");
        headerLabel.setFont(UiUtils.FONT_BOLD);
        headerPanel.add(headerLabel);

        contentPanel.add(headerPanel, "cell 0 0"); // Top

        // 2.2 Chat Area with LayeredPane
        // 使用 LayeredPane 实现悬浮按钮
        layeredChatPane = new JLayeredPane();
        layeredChatPane.setLayout(null); // 绝对布局用于 LayeredPane

        chatArea = new JPanel(new MigLayout("insets 10, fillx, wrap 1", "[grow, fill]", "[]"));
        chatArea.setBackground(Color.WHITE);

        chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // 添加滚动监听
        chatScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting())
                return;
            checkScrollPosition();
        });

        // 悬浮按钮 "回到底部"
        backToBottomBtn = new JButton("↓ 新消息");
        backToBottomBtn.setFont(UiUtils.FONT_NORMAL.deriveFont(12f));
        backToBottomBtn.setBackground(new Color(240, 248, 255));
        backToBottomBtn.setForeground(UiUtils.COLOR_PRIMARY);
        backToBottomBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 220, 240), 1, true),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        backToBottomBtn.setFocusPainted(false);
        backToBottomBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backToBottomBtn.setVisible(false);
        backToBottomBtn.addActionListener(e -> scrollToBottom());

        // 布局 LayeredPane
        // 监听大小变化以调整组件大小
        layeredChatPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredChatPane.getWidth();
                int h = layeredChatPane.getHeight();
                chatScrollPane.setBounds(0, 0, w, h);

                // 按钮位置：右下角悬浮
                int btnW = 100;
                int btnH = 30;
                backToBottomBtn.setBounds(w - btnW - 20, h - btnH - 20, btnW, btnH);
            }
        });

        layeredChatPane.add(chatScrollPane, JLayeredPane.DEFAULT_LAYER);
        layeredChatPane.add(backToBottomBtn, JLayeredPane.PALETTE_LAYER);

        contentPanel.add(layeredChatPane, "cell 0 1, grow"); // Center

        // 2.3 Input Area
        JPanel inputPanel = new JPanel(new MigLayout("insets 10, fill", "[grow, fill][]", "[grow, fill][]"));
        inputPanel.setBackground(Color.WHITE);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));

        // 工具栏 (暂时空白)
        // inputPanel.add(new JLabel("Tools"), "cell 0 0, span 2");

        // 输入框
        inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setBorder(null);
        inputArea.setFont(UiUtils.FONT_NORMAL.deriveFont(14f));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);

        inputPanel.add(inputScroll, "cell 0 0, span 2");

        // 发送按钮
        sendButton = new JButton("发送");
        sendButton.setBackground(UiUtils.COLOR_PRIMARY);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setFont(UiUtils.FONT_NORMAL);

        // 按钮容器，靠右
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.add(sendButton);

        inputPanel.add(btnPanel, "cell 1 1"); // Bottom Right of input area

        contentPanel.add(inputPanel, "cell 0 2"); // Bottom
    }

    // --- 消息添加方法 ---

    public void addMessage(boolean isSender, String text) {
        // 保存到数据库
        com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("TEXT", isSender, text, 0);
        com.bluelink.db.TransferDao.save(item);

        renderTextBubble(isSender, text);
    }

    public void addFileMessage(boolean isSender, File file) {
        com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("FILE", isSender,
                file.getAbsolutePath(), file.length());
        com.bluelink.db.TransferDao.save(item);

        renderFileBubble(isSender, file);
    }

    private void checkScrollPosition() {
        JScrollBar vBar = chatScrollPane.getVerticalScrollBar();
        int value = vBar.getValue();
        int extent = vBar.getModel().getExtent();
        int max = vBar.getMaximum();

        // 1. 判断是否触底
        // 给 20px 容差
        if (value + extent >= max - 20) {
            isUserAtBottom = true;
            backToBottomBtn.setVisible(false);
            backToBottomBtn.setText("↓ 回到底部"); // 重置文本
        } else {
            isUserAtBottom = false;
            // 如果不在底部，显示按钮 (这里可以优化为仅当有未读时显示，或者一直显示回到底部)
            // 简单逻辑：只要不在底部且距离较远，就显示
            if (max - (value + extent) > 100) {
                backToBottomBtn.setVisible(true);
            }
        }

        // 2. 判断是否触顶 (加载更多)
        if (value <= 10 && !isLoadingHistory) {
            loadMoreHistory();
        }
    }

    private void scrollToBottom() {
        // 关键逻辑：先触发布局更新，然后在下一个事件循环中滚到底部
        // 这能确保在添加大量内容或气泡变大后，滚动条的最大值已更新
        chatArea.revalidate();
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void loadMoreHistory() {
        if (hasLoadedAllHistory)
            return;

        isLoadingHistory = true;
        SwingUtilities.invokeLater(() -> {
            java.util.List<com.bluelink.db.TransferDao.LogItem> list = com.bluelink.db.TransferDao
                    .loadHistory(minLoadedId, 25);

            if (list.isEmpty()) {
                isLoadingHistory = false;
                if (!hasLoadedAllHistory) {
                    showNoMoreHistoryTip();
                    hasLoadedAllHistory = true;
                }
                return;
            } else if (list.size() < 25) {
                // 如果返回条数少于分页数，说明剩下的也加载完了，标记为已全部加载
                // 但这次的数据还是要渲染
                hasLoadedAllHistory = true;
                // 在渲染完这次的数据后，添加提示
                SwingUtilities.invokeLater(this::showNoMoreHistoryTip);
            }

            // 保持视口位置
            // 记录当前可视的第一条消息，或者简单地记录高度差
            JScrollBar vBar = chatScrollPane.getVerticalScrollBar();
            int oldMax = vBar.getMaximum();
            int oldValue = vBar.getValue();

            // 倒序插入到顶部
            // 因为 list 是按时间正序的 (旧 -> 新)
            // 我们要把它插入到 chatArea 的 index 0, 1, 2...
            // 所以应该倒着遍历 list，才能保持顺序正确？
            // 比如 list: [msg1, msg2, msg3] (msg1 最旧)
            // chatArea 现有: [msg4, msg5]
            // 我们希望: [msg1, msg2, msg3, msg4, msg5]
            // 所以先插 msg3 到 index 0 -> [msg3, msg4...]
            // 再插 msg2 到 index 0 -> [msg2, msg3...]
            // 再插 msg1 到 index 0 -> [msg1, msg2...]
            for (int i = list.size() - 1; i >= 0; i--) {
                com.bluelink.db.TransferDao.LogItem item = list.get(i);
                renderBubbleAtTop(item);
                if (item.id < minLoadedId) {
                    minLoadedId = item.id;
                }
            }

            chatArea.revalidate(); // 触发布局计算

            // 恢复视口
            SwingUtilities.invokeLater(() -> {
                int newMax = vBar.getMaximum();
                vBar.setValue(oldValue + (newMax - oldMax));
                isLoadingHistory = false;
            });
        });
    }

    private void renderBubbleAtTop(com.bluelink.db.TransferDao.LogItem item) {
        JPanel wrapper = createBubbleWrapper(item);
        chatArea.add(wrapper, "growx, wrap", 0); // index 0
    }

    private void showNoMoreHistoryTip() {
        JLabel tip = new JLabel("— 已显示全部历史消息 —");
        tip.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
        tip.setForeground(Color.GRAY);
        tip.setHorizontalAlignment(SwingConstants.CENTER);

        // 包装一下以适应 MigLayout
        JPanel wrapper = new JPanel(new MigLayout("insets 5, fillx, alignx center", "[center]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(tip);

        chatArea.add(wrapper, "growx, wrap", 0);
        chatArea.revalidate();
    }

    private JPanel createBubbleWrapper(com.bluelink.db.TransferDao.LogItem item) {
        boolean isSender = item.isSender;
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        com.bluelink.ui.bubble.BubblePanel bubble;
        if ("TEXT".equals(item.type)) {
            bubble = com.bluelink.ui.bubble.BubbleFactory.createTextBubble(isSender, item.content);
        } else {
            bubble = com.bluelink.ui.bubble.BubbleFactory.createFileBubble(isSender, new File(item.content));
        }

        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        // 处理状态
        if ("FAILED".equals(item.status)) {
            bubble.setStatus(true);
            bubble.setRetryAction(() -> performResend(item, bubble));
        }

        return wrapper;
    }

    private void performResend(com.bluelink.db.TransferDao.LogItem item, com.bluelink.ui.bubble.BubblePanel bubble) {
        System.out.println("DEBUG: performResend called for item " + item.id);
        // 重试逻辑
        bubble.setStatus(false); // 先清除错误状态

        new Thread(() -> {
            try {
                if ("TEXT".equals(item.type)) {
                    if (currentSession != null) {
                        currentSession.sendMessage(item.content);
                    } else {
                        client.send(item.content);
                    }
                } else if ("FILE".equals(item.type)) {
                    if (currentSession != null) {
                        currentSession.sendFile(new File(item.content));
                    } else {
                        client.sendFile(new File(item.content));
                    }
                }

                // 成功
                item.status = "SUCCESS";
                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "SUCCESS");
                } else {
                    com.bluelink.db.TransferDao.save(item);
                }

            } catch (Exception e) {
                // 再次失败
                SwingUtilities.invokeLater(() -> {
                    if (bubble != null) {
                        bubble.setStatus(true);
                    }
                });
                item.status = "FAILED";
                if (item.id > 0) {
                    com.bluelink.db.TransferDao.updateStatus(item.id, "FAILED");
                } else {
                    com.bluelink.db.TransferDao.save(item);
                }
            }
        }).start();
    }

    private com.bluelink.ui.bubble.BubblePanel renderTextBubble(boolean isSender, String text) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);
        com.bluelink.ui.bubble.BubblePanel bubble = com.bluelink.ui.bubble.BubbleFactory.createTextBubble(isSender,
                text);
        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap");

        handleNewMessageScroll();
        return bubble;
    }

    private com.bluelink.ui.bubble.BubblePanel renderFileBubble(boolean isSender, File file) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);
        com.bluelink.ui.bubble.BubblePanel bubble = com.bluelink.ui.bubble.BubbleFactory.createFileBubble(isSender,
                file);
        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap");

        handleNewMessageScroll();
        return bubble;
    }

    private void handleNewMessageScroll() {
        if (isUserAtBottom) {
            scrollToBottom();
        } else {
            // 提示新消息
            backToBottomBtn.setText("↓ 新消息");
            backToBottomBtn.setVisible(true);
        }
    }

    public void loadHistory() {
        // 初始加载最新的 25 条
        java.util.List<com.bluelink.db.TransferDao.LogItem> list = com.bluelink.db.TransferDao.loadHistory(-1, 25);
        for (com.bluelink.db.TransferDao.LogItem item : list) {
            // 这里按顺序添加到此时是空的 chatArea，所以直接 add 即可
            // 同时更新 minLoadedId
            if (item.id < minLoadedId) {
                minLoadedId = item.id;
            }
            // 复用逻辑
            if ("TEXT".equals(item.type)) {
                // renderTextBubble(item.isSender, item.content); // 以前的方法会强制滚到底部
                // 我们手动添加不触发滚动逻辑，等到最后统一滚到底
                JPanel w = createBubbleWrapper(item);
                chatArea.add(w, "growx, wrap");
            } else {
                JPanel w = createBubbleWrapper(item);
                chatArea.add(w, "growx, wrap");
            }
        }
        scrollToBottom();
    }

    public static void main(String[] args) {
        // 确保数据库初始化
        com.bluelink.db.DatabaseManager.initDatabase();

        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            ModernQQFrame frame = new ModernQQFrame();
            frame.loadHistory(); // 加载历史
            frame.setVisible(true);
        });
    }
}
