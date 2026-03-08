package com.bluelink.ui;

import com.bluelink.util.MdCodeUtil;
import com.bluelink.util.UiUtils;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.Disposable;

/**
 * 主窗口框架
 * 模仿 QQ NT 风格布局
 * 重构版：使用 MainChatPanel 和 AiChatPanel 组件
 */
public class ModernQQFrame extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(ModernQQFrame.class);

    // CardLayout 页面管理
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private ConnectionPanel connectionPanel;
    private static final String PAGE_CONNECTION = "connection";
    private static final String PAGE_CHAT = "chat";

    private JPanel rootPanel;
    private JPanel sidebarPanel;
    private JPanel contentPanel; // Holds MainChatPanel and AiChatPanel

    // 子组件
    private MainChatPanel mainChatPanel;
    private AiChatPanel aiChatPanel;
    private TrayManager trayManager;

    // Content container for switching between Chat and AI within root
    private static final String CARD_MAIN_CHAT = "main_chat";
    private static final String CARD_AI_CHAT = "ai_chat";

    // 状态记录
    private boolean enterToSend = com.bluelink.util.AppConfig.isEnterToSend();

    // 网络组件
    private com.bluelink.net.BluetoothServer server;
    private com.bluelink.net.BluetoothClient client;
    private com.bluelink.net.BluetoothSession currentSession;
    private String currentConnectedDeviceName;

    public ModernQQFrame() {
        initUI();
        initNetwork();
        trayManager = new TrayManager(this);

        // 注册关闭钩子，确保进程结束时清理资源
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }));
    }

    /**
     * 关闭应用并清理资源
     */
    public void shutdown() {
        System.out.println("[App] 正在关闭，清理资源...");
        if (server != null) {
            try {
                server.stop();
            } catch (Throwable t) {
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (Throwable t) {
            }
        }
        if (currentSession != null) {
            try {
                currentSession.close();
            } catch (Throwable t) {
            }
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

        // 2.2 右侧内容区 (CardLayout)
        createContentPanel();
        rootPanel.add(contentPanel, "cell 1 0");

        cardPanel.add(rootPanel, PAGE_CHAT);

        // 设置 cardPanel 为主内容
        setContentPane(cardPanel);

        // === 默认显示连接页面 ===
        cardLayout.show(cardPanel, PAGE_CONNECTION);

        enableDragAndDrop();
    }

    private void createContentPanel() {
        contentPanel = new JPanel(new CardLayout());

        mainChatPanel = new MainChatPanel(this);
        mainChatPanel.setClient(client); // Initial client
        if (currentConnectedDeviceName != null) {
            mainChatPanel.setConnectedDeviceName(currentConnectedDeviceName);
        }

        aiChatPanel = new AiChatPanel();

        contentPanel.add(mainChatPanel, CARD_MAIN_CHAT);
        contentPanel.add(aiChatPanel, CARD_AI_CHAT);
    }

    private void switchContent(String cardName) {
        ((CardLayout) contentPanel.getLayout()).show(contentPanel, cardName);
    }

    /**
     * 切换到聊天页面
     */
    private void showChatPage() {
        cardLayout.show(cardPanel, PAGE_CHAT);
        // Default to Main Chat
        switchContent(CARD_MAIN_CHAT);
        if (mainChatPanel != null) {
            mainChatPanel.setConnectedDeviceName(currentConnectedDeviceName);
        }
        // Load history if needed
        // SwingUtilities.invokeLater(() -> mainChatPanel.loadHistory()); // Removed
        // duplicate call
        // mainChatPanel will handle its own initial load or via user interaction

        // However, we DO need to trigger initial load if it hasn't been loaded.
        // But MainChatPanel handles scrolling to bottom on first load.
        // Let's call it ONCE here, but ensure MainChatPanel logic handles the "don't
        // double load"

        // Actually, if we call it here, it corresponds to the first "queryId=-1" in the
        // log.
        // If we DON'T call it here, who calls it?
        // The scroll listener is only added AFTER first load.
        // So we MUST call it here.
        SwingUtilities.invokeLater(() -> mainChatPanel.initHistory());
    }

    private com.bluelink.ai.SpringAiService aiService;

    public void setAiService(com.bluelink.ai.SpringAiService aiService) {
        this.aiService = aiService;
        if (aiChatPanel != null) {
            aiChatPanel.setAiService(aiService);
        }
        if (mainChatPanel != null) {
            mainChatPanel.setAiService(aiService);
        }
    }

    public void loadHistory() {
        if (mainChatPanel != null) {
            mainChatPanel.loadHistory();
        }
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

    // --- Sidebar Creation ---
    private void createSidebar() {
        sidebarPanel = new JPanel(new MigLayout("insets 10, flowy, alignx center, gap 0", "[center]"));
        sidebarPanel.setBackground(UiUtils.COLOR_BG_SIDEBAR);
        refreshSidebar();
    }

    private void refreshSidebar() {
        sidebarPanel.removeAll();

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
        sidebarPanel.add(codeLabel, "gaptop 5");

        if (com.bluelink.util.AppConfig.isAiEnabled()) {
            // 聊天入口按钮
            JComponent chatBtn = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    UiUtils.enableAntialiasing(g);
                    Graphics2D g2 = (Graphics2D) g;

                    g2.setColor(UiUtils.COLOR_PRIMARY);
                    Shape circle = new Ellipse2D.Double(0, 0, getWidth(), getHeight());
                    g2.fill(circle);

                    g2.setColor(Color.WHITE);
                    g2.setFont(UiUtils.FONT_BOLD.deriveFont(14f));
                    FontMetrics fm = g2.getFontMetrics();
                    String text = "聊";
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                    y -= 2;
                    g2.drawString(text, x, y);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(40, 40);
                }
            };
            chatBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chatBtn.setToolTipText("聊天列表");
            chatBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    switchContent(CARD_MAIN_CHAT);
                }
            });

            // AI 入口按钮
            JComponent aiBtn = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    UiUtils.enableAntialiasing(g);
                    Graphics2D g2 = (Graphics2D) g;

                    g2.setColor(new Color(110, 80, 200));
                    Shape circle = new Ellipse2D.Double(0, 0, getWidth(), getHeight());
                    g2.fill(circle);

                    g2.setColor(Color.WHITE);
                    g2.setFont(UiUtils.FONT_BOLD.deriveFont(14f));
                    FontMetrics fm = g2.getFontMetrics();
                    String text = "AI";
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                    y -= 2;
                    g2.drawString(text, x, y);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(40, 40);
                }
            };
            aiBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            aiBtn.setToolTipText("AI 助手");
            aiBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    switchContent(CARD_AI_CHAT);
                }
            });

            sidebarPanel.add(chatBtn, "gaptop 5");
            sidebarPanel.add(aiBtn, "gaptop 5");
        }

        sidebarPanel.revalidate();
        sidebarPanel.repaint();
    }

    // --- Network & DragDrop ---

    private void initNetwork() {
        server = new com.bluelink.net.BluetoothServer();
        server.setListener(new TransferListenerImpl());
        server.start();

        client = new com.bluelink.net.BluetoothClient();
        client.setListener(new TransferListenerImpl());

        if (mainChatPanel != null)
            mainChatPanel.setClient(client);
    }

    private class TransferListenerImpl implements com.bluelink.net.TransferListener {
        private final Map<com.bluelink.net.BluetoothSession, Disposable> aiSubscriptions = new ConcurrentHashMap<>();

        @Override
        public void onMessageReceived(String sender, String content) {
            com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("TEXT", false, content,
                    0);
            item.renderType = "TEXT";
            com.bluelink.db.TransferDao.save(item);
            SwingUtilities.invokeLater(() -> {
                mainChatPanel.addTextBubble(false, content);
                trayManager.showNotification("收到新消息", content);
            });
        }

        @Override
        public void onAiRequest(String prompt, com.bluelink.net.BluetoothSession session) {
            log.debug("[UI] 收到 AI 请求: {}", prompt);

            if (aiService == null) {
                try {
                    session.sendAiResponse("Error: Host AI 服务未就绪");
                    session.sendAiDone();
                } catch (Exception e) {
                }
                return;
            }

            Disposable existing = aiSubscriptions.remove(session);
            if (existing != null && !existing.isDisposed()) {
                existing.dispose();
            }

            Disposable disposable = aiService.streamChat(prompt)
                    .subscribe(
                            chunk -> {
                                try {
                                    session.sendAiResponse(chunk);
                                } catch (Exception e) {
                                    log.error("Failed to send AI response chunk", e);
                                }
                            },
                            err -> {
                                try {
                                    session.sendAiResponse("\n[Error: " + err.getMessage() + "]");
                                    session.sendAiDone();
                                } catch (Exception e) {
                                }
                                aiSubscriptions.remove(session);
                            },
                            () -> {
                                try {
                                    session.sendAiDone();
                                } catch (Exception e) {
                                }
                                aiSubscriptions.remove(session);
                            });
            aiSubscriptions.put(session, disposable);
        }

        @Override
        public void onAiResponse(String chunk) {
            SwingUtilities.invokeLater(() -> {
                if (mainChatPanel != null && mainChatPanel.isRemoteAiResponding()) {
                    mainChatPanel.onAiStreamChunk(chunk);
                } else if (aiChatPanel != null) {
                    aiChatPanel.onAiStreamChunk(chunk);
                }
            });
        }

        @Override
        public void onAiDone() {
            SwingUtilities.invokeLater(() -> {
                if (mainChatPanel != null && mainChatPanel.isRemoteAiResponding()) {
                    mainChatPanel.onAiStreamComplete();
                } else if (aiChatPanel != null && aiChatPanel.isRemoteAiResponding()) {
                    aiChatPanel.onAiStreamComplete();
                }
            });
        }

        @Override
        public void onAiStop(com.bluelink.net.BluetoothSession session) {
            Disposable disposable = aiSubscriptions.remove(session);
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
            try {
                session.sendAiDone();
            } catch (Exception e) {
            }
        }

        @Override
        public void onFileReceived(String sender, File file, String originalName) {
            com.bluelink.db.TransferDao.LogItem item = new com.bluelink.db.TransferDao.LogItem("FILE", false,
                    file.getAbsolutePath(), file.length());
            com.bluelink.db.TransferDao.save(item);
            SwingUtilities.invokeLater(() -> {
                mainChatPanel.onFileReceived(originalName, file);
                trayManager.showNotification("收到文件", file.getName());
            });
        }

        @Override
        public void onTransferProgress(String fileName, long current, long total, boolean isReceive) {
            SwingUtilities.invokeLater(() -> {
                mainChatPanel.onTransferProgress(fileName, current, total, isReceive);
            });
        }

        @Override
        public void onConnectionStatusChanged(boolean isConnected, String deviceName) {
            SwingUtilities.invokeLater(() -> {
                if (isConnected) {
                    currentConnectedDeviceName = deviceName;
                    showChatPage();
                    trayManager.showNotification("连接成功", "已与 " + deviceName + " 建立连接");
                    if (connectionPanel != null) {
                        connectionPanel.onIncomingConnection(deviceName);
                    }
                    if (mainChatPanel != null) {
                        mainChatPanel.setConnectedDeviceName(deviceName);
                    }
                    if (aiChatPanel != null) {
                        aiChatPanel.setConnectedDeviceName(deviceName);
                    }
                } else {
                    // 关闭旧连接
                    if (client != null)
                        client.close();
                    // 重建客户端实例，确保下次连接使用全新的干净状态
                    client = new com.bluelink.net.BluetoothClient();
                    client.setListener(new TransferListenerImpl());
                    if (mainChatPanel != null)
                        mainChatPanel.setClient(client);

                    if (currentSession != null)
                        currentSession = null;
                    currentConnectedDeviceName = null;
                    if (mainChatPanel != null)
                        mainChatPanel.setSession(null);
                    if (aiChatPanel != null)
                        aiChatPanel.setSession(null);
                    if (mainChatPanel != null)
                        mainChatPanel.setConnectedDeviceName(null);
                    if (aiChatPanel != null)
                        aiChatPanel.setConnectedDeviceName(null);
                    // 回到连接页，让用户重新发起连接
                    if (connectionPanel != null)
                        connectionPanel.resetState();
                    showConnectionPage(true);
                }
            });
        }

        @Override
        public void onSessionCreated(com.bluelink.net.BluetoothSession session) {
            currentSession = session;
            if (aiChatPanel != null)
                aiChatPanel.setSession(session);
            if (mainChatPanel != null)
                mainChatPanel.setSession(session);
            System.out.println("[UI] 会话已建立，保存 session");
        }

        @Override
        public void onError(String message) {
            SwingUtilities.invokeLater(() -> {
                System.err.println("错误: " + message);
                trayManager.showNotification("错误", message);
                if (connectionPanel != null && connectionPanel.isVisible()) {
                    connectionPanel.onConnectionFailed(message);
                }
            });
        }
    }

    // --- Settings Dialog ---
    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "设置", true);
        dialog.setUndecorated(true);
        dialog.setSize(520, 550);
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0, 0, 0, 0));

        java.util.concurrent.atomic.AtomicBoolean tempEnterToSend = new java.util.concurrent.atomic.AtomicBoolean(
                this.enterToSend);
        java.util.concurrent.atomic.AtomicInteger tempTimeout = new java.util.concurrent.atomic.AtomicInteger(
                com.bluelink.util.AppConfig.getConnectionTimeoutSeconds());
        java.util.concurrent.atomic.AtomicReference<String> tempPath = new java.util.concurrent.atomic.AtomicReference<>(
                com.bluelink.util.AppConfig.getDownloadPath());

        java.util.concurrent.atomic.AtomicBoolean tempAiEnabled = new java.util.concurrent.atomic.AtomicBoolean(
                com.bluelink.util.AppConfig.isAiEnabled());
        java.util.concurrent.atomic.AtomicBoolean tempAiMultiTurn = new java.util.concurrent.atomic.AtomicBoolean(
                com.bluelink.util.AppConfig.isAiMultiTurnEnabled());
        java.util.concurrent.atomic.AtomicInteger tempAiRetention = new java.util.concurrent.atomic.AtomicInteger(
                com.bluelink.util.AppConfig.getAiHistoryRetentionDays());
        java.util.concurrent.atomic.AtomicReference<String> tempAiUrl = new java.util.concurrent.atomic.AtomicReference<>(
                com.bluelink.util.AppConfig.getAiApiUrl());
        java.util.concurrent.atomic.AtomicReference<String> tempAiKey = new java.util.concurrent.atomic.AtomicReference<>(
                com.bluelink.util.AppConfig.getAiApiKey());
        java.util.concurrent.atomic.AtomicReference<String> tempAiModel = new java.util.concurrent.atomic.AtomicReference<>(
                com.bluelink.util.AppConfig.getAiModel());

        JButton saveBtn = new JButton("保存设置");
        saveBtn.setBackground(UiUtils.COLOR_PRIMARY);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(UiUtils.FONT_NORMAL);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);
        saveBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        saveBtn.setPreferredSize(new Dimension(100, 30));
        saveBtn.setEnabled(false);

        Runnable checkChanges = () -> {
            boolean changed = (tempEnterToSend.get() != this.enterToSend)
                    || (tempTimeout.get() != com.bluelink.util.AppConfig.getConnectionTimeoutSeconds())
                    || (!tempPath.get().equals(com.bluelink.util.AppConfig.getDownloadPath()))
                    || (tempAiEnabled.get() != com.bluelink.util.AppConfig.isAiEnabled())
                    || (tempAiMultiTurn.get() != com.bluelink.util.AppConfig.isAiMultiTurnEnabled())
                    || (tempAiRetention.get() != com.bluelink.util.AppConfig.getAiHistoryRetentionDays())
                    || (!tempAiUrl.get().equals(com.bluelink.util.AppConfig.getAiApiUrl()))
                    || (!tempAiKey.get().equals(com.bluelink.util.AppConfig.getAiApiKey()))
                    || (!tempAiModel.get().equals(com.bluelink.util.AppConfig.getAiModel()));

            saveBtn.setEnabled(changed);
        };
        checkChanges.run();

        Runnable closeAction = () -> {
            if (saveBtn.isEnabled()) {
                int opt = JOptionPane.showConfirmDialog(dialog, "设置未保存，确定退出吗？");
                if (opt == JOptionPane.YES_OPTION)
                    dialog.dispose();
            } else
                dialog.dispose();
        };

        saveBtn.addActionListener(e -> {
            boolean wasMultiTurn = com.bluelink.util.AppConfig.isAiMultiTurnEnabled();
            boolean isMultiTurn = tempAiMultiTurn.get();

            this.enterToSend = tempEnterToSend.get();
            com.bluelink.util.AppConfig.setEnterToSend(this.enterToSend);
            com.bluelink.util.AppConfig.setConnectionTimeout(tempTimeout.get());
            com.bluelink.util.AppConfig.setDownloadPath(tempPath.get());
            com.bluelink.util.AppConfig.setAiEnabled(tempAiEnabled.get());
            com.bluelink.util.AppConfig.setAiMultiTurnEnabled(isMultiTurn);
            com.bluelink.util.AppConfig.setAiHistoryRetentionDays(tempAiRetention.get());
            com.bluelink.util.AppConfig.setAiApiUrl(tempAiUrl.get());
            com.bluelink.util.AppConfig.setAiApiKey(tempAiKey.get());
            com.bluelink.util.AppConfig.setAiModel(tempAiModel.get());

            // Handle Redis lifecycle with UI feedback
            if (aiService != null) {
                if (isMultiTurn && !wasMultiTurn) {
                    // Starting Redis
                    JDialog loading = new JDialog(dialog, "请稍候", true);
                    loading.setUndecorated(true);
                    JPanel p = new JPanel(new MigLayout("insets 20", "[center]", "[][center]"));
                    p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                    p.add(new JLabel("正在启动多轮对话引擎..."), "wrap");
                    JProgressBar bar = new JProgressBar();
                    bar.setIndeterminate(true);
                    p.add(bar);
                    loading.setContentPane(p);
                    loading.pack();
                    loading.setLocationRelativeTo(dialog);

                    new Thread(() -> {
                        aiService.startRedis();
                        SwingUtilities.invokeLater(loading::dispose);
                    }).start();

                    loading.setVisible(true);
                } else if (!isMultiTurn && wasMultiTurn) {
                    // Stopping Redis
                    new Thread(() -> aiService.stopRedis()).start();
                }
            }

            // Refresh sidebar to reflect AI toggle
            refreshSidebar();
            if (!tempAiEnabled.get()) {
                switchContent(CARD_MAIN_CHAT);
            }

            // 通知 AI 面板更新状态 (例如启用/禁用下拉框)
            if (aiChatPanel != null) {
                aiChatPanel.onMultiTurnToggled(isMultiTurn);
            }

            dialog.dispose();
        });

        // Use helper to keep main readable
        JPanel mainPanel = createSettingsContent(dialog, closeAction, saveBtn, tempEnterToSend, tempTimeout, tempPath,
                tempAiEnabled, tempAiMultiTurn, tempAiRetention, tempAiUrl, tempAiKey, tempAiModel, checkChanges);

        dialog.setContentPane(mainPanel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeAction.run();
            }
        });
        dialog.setVisible(true);
    }

    private JPanel createSettingsContent(JDialog dialog, Runnable closeAction, JButton saveBtn,
            java.util.concurrent.atomic.AtomicBoolean tempEnterToSend,
            java.util.concurrent.atomic.AtomicInteger tempTimeout,
            java.util.concurrent.atomic.AtomicReference<String> tempPath,
            java.util.concurrent.atomic.AtomicBoolean tempAiEnabled,
            java.util.concurrent.atomic.AtomicBoolean tempAiMultiTurn,
            java.util.concurrent.atomic.AtomicInteger tempAiRetention,
            java.util.concurrent.atomic.AtomicReference<String> tempAiUrl,
            java.util.concurrent.atomic.AtomicReference<String> tempAiKey,
            java.util.concurrent.atomic.AtomicReference<String> tempAiModel,
            Runnable checkChanges) {
        JPanel mainPanel = new JPanel() {
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
        mainPanel.setLayout(new MigLayout("insets 20 25 10 25, fill, wrap 1", "[grow]", "[]10[]10[grow]0[]"));

        // Title
        JPanel titlePanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("设置");
        title.setFont(UiUtils.FONT_BOLD.deriveFont(18f));
        JButton close = new JButton("x");
        close.setBorder(null);
        close.setContentAreaFilled(false);
        close.setFont(UiUtils.FONT_BOLD.deriveFont(16f));
        close.setCursor(new Cursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> closeAction.run());
        titlePanel.add(title);
        titlePanel.add(close);
        mainPanel.add(titlePanel, "growx, gapbottom 0");

        // --- 本机码卡片 ---
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

        // Tabs
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
        tabs.setFocusable(false);
        tabs.setFont(UiUtils.FONT_NORMAL.deriveFont(14f));
        tabs.setBackground(Color.WHITE);
        tabs.setOpaque(true);

        // Tab 1: Send
        JPanel sendPanel = new JPanel(new MigLayout("insets 10, fillx, wrap 1"));
        sendPanel.setOpaque(false);
        JRadioButton r1 = new JRadioButton("Enter 发送", tempEnterToSend.get());
        JRadioButton r2 = new JRadioButton("Ctrl+Enter 发送", !tempEnterToSend.get());
        r1.setFont(UiUtils.FONT_NORMAL);
        r2.setFont(UiUtils.FONT_NORMAL);
        r1.setOpaque(false);
        r2.setOpaque(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(r1);
        bg.add(r2);
        r1.addActionListener(e -> {
            tempEnterToSend.set(true);
            checkChanges.run();
        });
        r2.addActionListener(e -> {
            tempEnterToSend.set(false);
            checkChanges.run();
        });
        sendPanel.add(r1);
        sendPanel.add(r2);
        tabs.addTab("发送", sendPanel);

        // Tab 2: General (Timeout, Path)
        JPanel genPanel = new JPanel(new MigLayout("insets 10, fillx, wrap 1"));
        genPanel.setOpaque(false);

        // Timeout
        genPanel.add(new JLabel("连接超时 (秒):"), "split 2");
        JTextField timeoutF = new JTextField(String.valueOf(tempTimeout.get()), 5);
        timeoutF.getDocument().addDocumentListener(getSimpleListener(() -> {
            try {
                tempTimeout.set(Integer.parseInt(timeoutF.getText().trim()));
            } catch (Exception e) {
            }
            checkChanges.run();
        }));
        genPanel.add(timeoutF, "wrap");

        // Path
        genPanel.add(new JLabel("文件保存位置:"), "wrap");
        JPanel pathRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        pathRow.setOpaque(false);
        JTextField pathF = new JTextField(tempPath.get());
        pathF.setEditable(false);
        JButton changePath = new JButton("浏览...");
        changePath.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                String p = chooser.getSelectedFile().getAbsolutePath();
                pathF.setText(p);
                tempPath.set(p);
                checkChanges.run();
            }
        });
        pathRow.add(pathF, "growx");
        pathRow.add(changePath);
        genPanel.add(pathRow, "growx");

        tabs.addTab("通用", genPanel);

        // Tab 3: AI
        JPanel aiPanel = new JPanel(new MigLayout("insets 10, fillx, wrap 1"));
        aiPanel.setOpaque(false);

        JPanel switchPanel = new JPanel(new MigLayout("insets 0", "[]10[]"));
        switchPanel.setOpaque(false);

        SwitchButton enableAiSwitch = new SwitchButton(tempAiEnabled.get());
        enableAiSwitch.addActionListener(e -> {
            tempAiEnabled.set(enableAiSwitch.isSelected());
            checkChanges.run();
        });

        JLabel switchLabel = new JLabel("启用 AI 功能");
        switchLabel.setFont(UiUtils.FONT_NORMAL);

        switchPanel.add(enableAiSwitch);
        switchPanel.add(switchLabel);

        aiPanel.add(switchPanel, "wrap");

        JPanel multiTurnPanel = new JPanel(new MigLayout("insets 0", "[]10[]"));
        multiTurnPanel.setOpaque(false);

        // Retention Days
        JPanel retentionPanel = new JPanel(new MigLayout("insets 0", "[][grow]"));
        retentionPanel.setOpaque(false);
        retentionPanel.add(new JLabel("历史记录保留 (天):"));
        // 使用 JFormattedTextField 限制只能输入数字
        javax.swing.text.NumberFormatter numberFormatter = new javax.swing.text.NumberFormatter(
                java.text.NumberFormat.getIntegerInstance());
        numberFormatter.setValueClass(Integer.class);
        numberFormatter.setAllowsInvalid(true); // 允许暂时输入非法字符（如空），以便用户清空重输
        numberFormatter.setMinimum(1); // 最小 1 天
        numberFormatter.setMaximum(365); // 最大 365 天

        JFormattedTextField retentionF = new JFormattedTextField(numberFormatter);
        retentionF.setValue(tempAiRetention.get());
        retentionF.setColumns(5);

        // 关键修复：设置 FocusLostBehavior 为 PERSIST，允许用户暂时清空内容进行编辑
        // 默认是 COMMIT_OR_REVERT，如果清空（非法值），焦点丢失时会回滚。
        // 但如果在输入过程中（焦点未丢失）内容为空，getValue() 可能会抛异常或返回 null。
        retentionF.setFocusLostBehavior(JFormattedTextField.PERSIST);

        retentionF.getDocument().addDocumentListener(getSimpleListener(() -> {
            try {
                // 如果内容为空，暂时不 commit，也不更新 tempAiRetention
                if (retentionF.getText().trim().isEmpty()) {
                    return;
                }

                // 尝试 commit
                retentionF.commitEdit();
                Object val = retentionF.getValue();
                if (val instanceof Number) {
                    int days = ((Number) val).intValue();
                    if (days != tempAiRetention.get()) {
                        tempAiRetention.set(days);
                        checkChanges.run();
                    }
                }
            } catch (Exception e) {
                // ignore invalid
            }
        }));

        // 同时也监听 PropertyChange
        retentionF.addPropertyChangeListener("value", evt -> {
            try {
                Object val = retentionF.getValue();
                if (val instanceof Number) {
                    int days = ((Number) val).intValue();
                    if (days != tempAiRetention.get()) {
                        tempAiRetention.set(days);
                        checkChanges.run();
                    }
                }
            } catch (Exception e) {
            }
        });

        retentionPanel.add(retentionF);

        // 初始可见性
        retentionPanel.setVisible(tempAiMultiTurn.get());
        aiPanel.add(retentionPanel, "wrap");

        SwitchButton multiTurnSwitch = new SwitchButton(tempAiMultiTurn.get());
        multiTurnSwitch.addActionListener(e -> {
            boolean selected = multiTurnSwitch.isSelected();
            tempAiMultiTurn.set(selected);
            retentionPanel.setVisible(selected);

            // 立即触发 Redis 启停逻辑 (如果需要)
            // 注意：这只是为了演示，真正的启停应该在点击"保存设置"后触发
            // 但用户要求 "为了用户体验可以有一个等待动画"
            // 这意味着我们可能需要在点击保存时，如果检测到 multiTurn 从 false -> true，则显示动画

            checkChanges.run();
        });

        JLabel multiTurnLabel = new JLabel("启用多轮对话");
        multiTurnLabel.setFont(UiUtils.FONT_NORMAL);

        multiTurnPanel.add(multiTurnSwitch);
        multiTurnPanel.add(multiTurnLabel);

        aiPanel.add(multiTurnPanel, "wrap", 1);

        aiPanel.add(new JLabel("API URL:"));
        JTextField urlF = new JTextField(tempAiUrl.get());
        urlF.getDocument().addDocumentListener(getSimpleListener(() -> {
            tempAiUrl.set(urlF.getText());
            checkChanges.run();
        }));
        aiPanel.add(urlF, "growx");

        aiPanel.add(new JLabel("API Key:"));
        JTextField keyF = new JTextField(tempAiKey.get());
        keyF.getDocument().addDocumentListener(getSimpleListener(() -> {
            tempAiKey.set(keyF.getText());
            checkChanges.run();
        }));
        aiPanel.add(keyF, "growx");

        aiPanel.add(new JLabel("Model Name:"));
        JTextField modelF = new JTextField(tempAiModel.get());
        modelF.getDocument().addDocumentListener(getSimpleListener(() -> {
            tempAiModel.set(modelF.getText());
            checkChanges.run();
        }));
        aiPanel.add(modelF, "growx");

        tabs.addTab("AI", aiPanel);

        // Tab 4: Connect
        JPanel connPanel = new JPanel(new MigLayout("insets 15, fillx, wrap 1", "[grow]"));
        connPanel.setOpaque(false);

        JLabel connTip = new JLabel("添加新设备连接");
        connTip.setFont(UiUtils.FONT_BOLD);
        connPanel.add(connTip, "align left, gaptop 5, gapbottom 20");

        JButton goConn = new JButton("前往连接页面");
        goConn.setBackground(UiUtils.COLOR_PRIMARY);
        goConn.setForeground(Color.WHITE);
        goConn.setFont(UiUtils.FONT_NORMAL);
        goConn.setFocusPainted(false);
        goConn.setBorderPainted(false);
        goConn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        goConn.setPreferredSize(new Dimension(180, 40));
        goConn.addActionListener(e -> {
            // Check changes before leaving
            if (saveBtn.isEnabled()) {
                int opt = JOptionPane.showConfirmDialog(dialog, "设置未保存，确定离开吗？", "未保存警告",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (opt != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            dialog.dispose();
            showConnectionPage(true);
        });
        connPanel.add(goConn, "align center, gaptop 10");
        tabs.addTab("连接", connPanel);

        // Tab 5: About
        JPanel aboutPanel = new JPanel(new MigLayout("insets 10, fillx, wrap 1"));
        aboutPanel.setOpaque(false);
        aboutPanel.add(new JLabel("<html><b>BlueLink</b> v2.0.1-DEV</html>"), "wrap");
        aboutPanel.add(new JLabel("作者: ZPC"), "wrap");
        aboutPanel.add(new JLabel("Email: privacyporton@proton.me"), "wrap");

        JLabel gitLink = new JLabel("<html><a href='#'>https://github.com/123zpc/BlueLink</a></html>");
        gitLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gitLink.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI("https://github.com/123zpc/BlueLink"));
                } catch (Exception ex) {
                }
            }
        });
        aboutPanel.add(gitLink);

        tabs.addTab("关于", aboutPanel);

        // SVG 图标和颜色切换
        try {
            FlatSVGIcon iconSend = new FlatSVGIcon("com/bluelink/ui/icons/send.svg", 16, 16);
            FlatSVGIcon iconSettings = new FlatSVGIcon("com/bluelink/ui/icons/settings.svg", 16, 16);
            FlatSVGIcon iconAi = new FlatSVGIcon("com/bluelink/ui/icons/ai.svg", 16, 16);
            FlatSVGIcon iconConnect = new FlatSVGIcon("com/bluelink/ui/icons/connect.svg", 16, 16);
            FlatSVGIcon iconAbout = new FlatSVGIcon("com/bluelink/ui/icons/about.svg", 16, 16);

            // 颜色过滤器
            FlatSVGIcon.ColorFilter blueFilter = new FlatSVGIcon.ColorFilter(color -> UiUtils.COLOR_PRIMARY);

            // 重新设置带图标的 Tab
            tabs.setIconAt(0, iconSend);
            tabs.setIconAt(1, iconSettings);
            tabs.setIconAt(2, iconAi);
            tabs.setIconAt(3, iconConnect);
            tabs.setIconAt(4, iconAbout);

            // Tab 切换监听器 - 实现颜色变化
            tabs.addChangeListener(e -> {
                int selected = tabs.getSelectedIndex();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    String tabTitle = tabs.getTitleAt(i);
                    // 移除 HTML 标签获取纯文本
                    String plain = tabTitle.replaceAll("<[^>]*>", "").trim();

                    // 重置图标颜色
                    Icon currentIcon = tabs.getIconAt(i);
                    if (currentIcon instanceof FlatSVGIcon) {
                        ((FlatSVGIcon) currentIcon).setColorFilter(null);
                    }

                    if (i == selected) {
                        // 选中：蓝色文本，固定宽度
                        tabs.setTitleAt(i, "<html><div style='width: 65px; text-align: left; color: #0099FF'>" + plain
                                + "</div></html>");
                        if (currentIcon instanceof FlatSVGIcon) {
                            ((FlatSVGIcon) currentIcon).setColorFilter(blueFilter);
                        }
                    } else {
                        // 未选中：默认颜色，固定宽度
                        tabs.setTitleAt(i, "<html><div style='width: 65px; text-align: left; color: #000000'>" + plain
                                + "</div></html>");
                    }
                }
                tabs.repaint();
            });

            // 强制触发第一个 Tab 的样式
            if (tabs.getTabCount() > 0) {
                tabs.setSelectedIndex(-1);
                tabs.setSelectedIndex(0);
            }
        } catch (Exception e) {
            // 图标加载失败时的降级处理
            System.err.println("Failed to load SVG icons: " + e.getMessage());
        }

        mainPanel.add(tabs, "grow");

        // Bottom
        JPanel bot = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
        bot.setOpaque(false);

        JButton clearBtn = new JButton("清空所有记录");
        clearBtn.setForeground(new Color(220, 60, 60));
        clearBtn.setFont(UiUtils.FONT_NORMAL);
        clearBtn.setBorderPainted(false);
        clearBtn.setContentAreaFilled(false);
        clearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dialog, "确定清空所有通过记录吗？", "确认",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                clearChatHistory();
            }
        });

        bot.add(clearBtn);
        bot.add(saveBtn);
        mainPanel.add(bot, "growx");

        return mainPanel;
    }

    private void clearChatHistory() {
        com.bluelink.db.TransferDao.clearAll();
        if (mainChatPanel != null)
            mainChatPanel.clearChat();
        if (aiChatPanel != null)
            aiChatPanel.clearChat();
    }

    private javax.swing.event.DocumentListener getSimpleListener(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }
        };
    }

    public static void copyImageToClipboard(java.awt.Image image) {
        java.awt.datatransfer.Transferable trans = new java.awt.datatransfer.Transferable() {
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[] { java.awt.datatransfer.DataFlavor.imageFlavor };
            }

            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
            }

            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                    throws java.awt.datatransfer.UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor))
                    throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                return image;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(trans, null);
    }

    private void enableDragAndDrop() {
        java.awt.dnd.DropTargetAdapter dropListener = new java.awt.dnd.DropTargetAdapter() {
            public void drop(java.awt.dnd.DropTargetDropEvent dtde) {
                try {
                    if (dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                        @SuppressWarnings("unchecked")
                        java.util.List<File> droppedFiles = (java.util.List<File>) dtde.getTransferable()
                                .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);

                        for (File file : droppedFiles) {
                            if (mainChatPanel != null)
                                mainChatPanel.performFileSend(file);
                        }
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        };

        JPanel glassPane = new JPanel();
        glassPane.setOpaque(false);
        glassPane.setLayout(null);
        new java.awt.dnd.DropTarget(glassPane, dropListener);
        this.setGlassPane(glassPane);
        glassPane.setVisible(true);
    }

    public static void main(String[] args) {
        com.bluelink.db.DatabaseManager.initDatabase();
        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            ModernQQFrame frame = new ModernQQFrame();
            frame.setVisible(true);
        });
    }
}
