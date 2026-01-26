package com.bluelink.ui;

import com.bluelink.util.AppConfig;
import com.bluelink.util.BluetoothUtils;
import com.bluelink.util.MdCodeUtil;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 启动时的连接页面
 * 用户需选择连接模式后才能进入聊天界面
 */
public class ConnectionPanel extends JPanel {

    public interface ConnectionCallback {
        void onConnected(boolean isHost, String peerAddress);

        void onDevModeEnter();

        void onSkip(); // 跳过连接，进入聊天界面
    }

    private final ConnectionCallback callback;
    private JLabel statusLabel;
    private JButton waitBtn, connectBtn, scanBtn, skipBtn;
    private CodeInputPanel codeInput;
    private boolean isWaiting = false;
    private boolean isScanning = false;
    private Timer timeoutTimer;
    private Thread scanThread;

    public ConnectionPanel(ConnectionCallback callback) {
        this.callback = callback;
        initUI();
    }

    private void initUI() {
        setLayout(new MigLayout("insets 20 40 40 40, fill, wrap 1", "[grow]", "[][center, grow]push[]"));
        setBackground(Color.WHITE);

        // --- 顶部右上角: 跳过/返回按钮 ---
        skipBtn = new JButton("离线模式");
        skipBtn.setFont(UiUtils.FONT_NORMAL.deriveFont(12f));
        skipBtn.setForeground(new Color(100, 100, 100));
        skipBtn.setBorderPainted(false);
        skipBtn.setContentAreaFilled(false);
        skipBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        skipBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                skipBtn.setForeground(UiUtils.COLOR_PRIMARY);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                skipBtn.setForeground(new Color(100, 100, 100));
            }
        });
        skipBtn.addActionListener(e -> {
            if (callback != null) {
                callback.onSkip();
            }
        });
        add(skipBtn, "right");

        // --- 头部：头像和连接码 ---
        JPanel headerPanel = new JPanel(new MigLayout("wrap 1, insets 0", "[center]"));
        headerPanel.setOpaque(false);

        // 头像 - 使用圆形绘制
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiUtils.COLOR_PRIMARY);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(UiUtils.FONT_BOLD.deriveFont(32f));
                FontMetrics fm = g2.getFontMetrics();
                String text = "B";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(text, x, y);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(80, 80);
            }
        };
        avatar.setOpaque(false);

        // 应用名
        JLabel appName = new JLabel("BlueLink");
        appName.setFont(UiUtils.FONT_BOLD.deriveFont(24f));
        appName.setForeground(new Color(50, 50, 50));

        // 我的连接码
        JLabel codeTitle = new JLabel("我的连接码");
        codeTitle.setFont(UiUtils.FONT_NORMAL.deriveFont(12f));
        codeTitle.setForeground(Color.GRAY);

        String myCode = MdCodeUtil.getMyCode();

        // 连接码区域（连接码 + 复制图标在同一行）
        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        codeRow.setOpaque(false);

        JLabel codeLabel = new JLabel(myCode);
        codeLabel.setFont(new Font("Consolas", Font.BOLD, 36));
        codeLabel.setForeground(UiUtils.COLOR_PRIMARY);

        JButton copyBtn = UiUtils.createCopyButton(myCode);

        codeRow.add(codeLabel);
        codeRow.add(copyBtn);

        headerPanel.add(avatar);
        headerPanel.add(appName, "gaptop 10");
        headerPanel.add(codeTitle, "gaptop 20");
        headerPanel.add(codeRow, "gaptop 5");

        add(headerPanel, "align center");

        // --- 模式选择 ---
        JPanel modePanel = new JPanel(new MigLayout("insets 0, gap 20", "[][]"));
        modePanel.setOpaque(false);

        waitBtn = createModeButton("等待连接", "作为被连接方，等待他人扫描连接");
        connectBtn = createModeButton("连接他人", "作为连接方，输入对方连接码");

        waitBtn.addActionListener(e -> startWaiting());
        connectBtn.addActionListener(e -> showConnectInput());

        modePanel.add(waitBtn);
        modePanel.add(connectBtn);
        add(modePanel, "align center");

        // --- 连接输入区域 ---
        JPanel inputPanel = new JPanel(new MigLayout("insets 0, wrap 1", "[center]"));
        inputPanel.setOpaque(false);

        codeInput = new CodeInputPanel();
        codeInput.setOnComplete(this::startScan);

        scanBtn = new JButton("扫描连接");
        scanBtn.setFont(UiUtils.FONT_BOLD);
        scanBtn.setBackground(UiUtils.COLOR_PRIMARY);
        scanBtn.setForeground(Color.WHITE);
        scanBtn.setFocusPainted(false);
        scanBtn.setBorderPainted(false);
        scanBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        scanBtn.setPreferredSize(new Dimension(150, 40));
        scanBtn.addActionListener(e -> startScan());

        inputPanel.add(codeInput);
        inputPanel.add(scanBtn, "gaptop 15");
        inputPanel.setVisible(false); // 初始隐藏

        add(inputPanel, "id inputPanel, align center");

        // --- 状态提示 ---
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiUtils.FONT_NORMAL.deriveFont(12f));
        statusLabel.setForeground(Color.GRAY);
        add(statusLabel, "align center");

        // 跳过按钮已移到顶部右上角

        // --- 备注文字 ---
        JLabel noteLabel = new JLabel(
                "<html><center><font color='#999999'>首次连接需在 Windows 中确认配对<br>配对后可自动连接</font></center></html>");
        noteLabel.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
        add(noteLabel, "gaptop 10, align center");

    }

    private JButton createModeButton(String title, String tooltip) {
        JButton btn = new JButton(title);
        btn.setFont(UiUtils.FONT_BOLD.deriveFont(14f));
        btn.setPreferredSize(new Dimension(130, 50));
        btn.setBackground(new Color(248, 250, 255));
        btn.setForeground(new Color(80, 80, 80));
        btn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(235, 245, 255));
                btn.setBorder(BorderFactory.createLineBorder(UiUtils.COLOR_PRIMARY, 2));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(248, 250, 255));
                btn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
            }
        });

        return btn;
    }

    private void startWaiting() {
        isWaiting = true;

        // 动态改变按钮状态
        waitBtn.setText("取消等待");
        waitBtn.setForeground(new Color(200, 80, 80));
        waitBtn.setBorder(BorderFactory.createLineBorder(new Color(200, 80, 80), 2));
        // 移除旧的监听器
        for (java.awt.event.ActionListener l : waitBtn.getActionListeners()) {
            waitBtn.removeActionListener(l);
        }
        waitBtn.addActionListener(e -> cancelWaiting());

        connectBtn.setEnabled(false);
        statusLabel.setText("正在等待其他设备连接...");

        // 隐藏输入区域
        for (Component c : getComponents()) {
            if ("inputPanel".equals(c.getName())) {
                c.setVisible(false);
            }
        }

        // 设置超时定时器 - 等待连接模式不设置超时，仅手动取消
        // timeoutTimer = new Timer(AppConfig.getConnectionTimeoutMs(), e -> { ... });
        // timeoutTimer.start();

        // 通知回调
        if (callback != null) {
            callback.onConnected(true, null);
        }
    }

    /**
     * 取消等待
     */
    private void cancelWaiting() {
        isWaiting = false;

        // 停止超时定时器
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }

        // 恢复按钮状态
        waitBtn.setText("等待连接");
        waitBtn.setForeground(UiUtils.COLOR_PRIMARY);
        waitBtn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
        // 移除旧的监听器
        for (java.awt.event.ActionListener l : waitBtn.getActionListeners()) {
            waitBtn.removeActionListener(l);
        }
        waitBtn.addActionListener(e -> startWaiting());

        connectBtn.setEnabled(true);

        statusLabel.setText(" ");
        statusLabel.setForeground(Color.GRAY);

        revalidate();
        repaint();
    }

    /**
     * 取消扫描
     */
    private void cancelScanning() {
        isScanning = false;

        // 中断扫描线程
        if (scanThread != null) {
            scanThread.interrupt();
            scanThread = null;
        }

        // 停止超时定时器
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }

        // 恢复扫描按钮
        scanBtn.setText("扫描连接");
        scanBtn.setEnabled(true);

        // 恢复连接按钮状态
        connectBtn.setText("连接他人");
        connectBtn.setForeground(UiUtils.COLOR_PRIMARY);
        connectBtn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
        for (java.awt.event.ActionListener l : connectBtn.getActionListeners()) {
            connectBtn.removeActionListener(l);
        }
        connectBtn.addActionListener(e -> showConnectInput());

        codeInput.setEnabled(true);
        waitBtn.setEnabled(true);

        statusLabel.setText("扫描已取消");
        statusLabel.setForeground(Color.GRAY);

        revalidate();
        repaint();
    }

    private void showConnectInput() {
        // 显示输入区域
        for (Component c : getComponents()) {
            c.setVisible(true);
        }
        statusLabel.setText("输入对方连接码，点击扫描");
        codeInput.focusFirst();
    }

    private void startScan() {
        if (isScanning) return; // 防止重复点击

        String targetCode = codeInput.getCode().trim(); // 去除可能的空白字符
        System.out.println("开始扫描，目标连接码: '" + targetCode + "'");

        // 开发模式检查
        if (AppConfig.isDevBypassCode(targetCode)) {
            statusLabel.setText("开发模式：直接进入");
            if (callback != null) {
                callback.onDevModeEnter();
            }
            return;
        }

        if (targetCode.length() != 6 || !targetCode.matches("\\d{6}")) {
            statusLabel.setText("请输入有效的 6 位数字连接码");
            statusLabel.setForeground(new Color(200, 80, 80));
            return;
        }

        isScanning = true;

        // 禁用所有控件
        scanBtn.setEnabled(false);
        scanBtn.setText("扫描中...");
        codeInput.setEnabled(false);
        waitBtn.setEnabled(false);

        // 动态改变"连接他人"按钮为"取消"按钮
        connectBtn.setText("取消扫描");
        connectBtn.setForeground(new Color(200, 80, 80));
        connectBtn.setBorder(BorderFactory.createLineBorder(new Color(200, 80, 80), 2));
        for (java.awt.event.ActionListener l : connectBtn.getActionListeners()) {
            connectBtn.removeActionListener(l);
        }
        connectBtn.addActionListener(e -> cancelScanning());

        // 设置超时定时器
        timeoutTimer = new Timer(AppConfig.getConnectionTimeoutMs(), e -> {
            System.out.println("超时定时器触发！");
            SwingUtilities.invokeLater(() -> {
                // 先恢复控件状态
                isScanning = false;
                if (scanThread != null) {
                    scanThread.interrupt();
                    scanThread = null;
                }

                scanBtn.setText("扫描连接");
                scanBtn.setEnabled(true);

                connectBtn.setText("连接他人");
                connectBtn.setForeground(UiUtils.COLOR_PRIMARY);
                connectBtn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
                for (java.awt.event.ActionListener l : connectBtn.getActionListeners()) {
                    connectBtn.removeActionListener(l);
                }
                connectBtn.addActionListener(evt -> showConnectInput());

                codeInput.setEnabled(true);
                waitBtn.setEnabled(true);

                // 显示超时提示
                statusLabel.setText("连接超时，请手动连接");
                statusLabel.setForeground(new Color(200, 80, 80));

                revalidate();
                repaint();
            });
        });
        timeoutTimer.setRepeats(false);
        timeoutTimer.start();

        statusLabel.setText("正在扫描附近蓝牙设备...");
        statusLabel.setForeground(Color.GRAY);

        // 异步扫描
        scanThread = new Thread(() -> {
            try {
                System.out.println("正在执行设备发现...");
                List<BluetoothUtils.BluetoothDevice> devices = BluetoothUtils.discoverDevices(10);
                System.out.println("设备发现完成，找到 " + devices.size() + " 个设备");
                
                BluetoothUtils.BluetoothDevice target = BluetoothUtils.findDeviceByCode(targetCode, devices);
                if (target != null) {
                    System.out.println("找到目标设备: " + target);
                } else {
                    System.out.println("未找到目标设备 (code=" + targetCode + ")");
                    for (BluetoothUtils.BluetoothDevice d : devices) {
                        System.out.println("  - 候选设备: " + d.code + " (" + d.name + ")");
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    if (target != null) {
                        // 找到设备，必须停止超时定时器，防止干扰
                        if (timeoutTimer != null) {
                            timeoutTimer.stop();
                            timeoutTimer = null;
                        }

                        // 找到设备，保持禁用状态，继续连接
                        statusLabel.setText("正在连接: " + target.name + "...");
                        statusLabel.setForeground(new Color(60, 160, 60));
                        statusLabel.setCursor(Cursor.getDefaultCursor());
                        // 移除之前的点击监听器
                        for (java.awt.event.MouseListener l : statusLabel.getMouseListeners()) {
                            statusLabel.removeMouseListener(l);
                        }

                        // 更新按钮文字
                        scanBtn.setText("连接中...");

                        // 直接连接（无需确认）
                        if (callback != null) {
                            System.out.println("调用 onConnected 回调: " + target.address);
                            callback.onConnected(false, String.valueOf(target.address));
                        }
                    } else {
                        // 未找到设备，停止超时定时器，恢复控件
                        if (timeoutTimer != null) {
                            timeoutTimer.stop();
                            timeoutTimer = null;
                        }
                        cancelScanning();

                        // 显示可点击的设备数量
                        if (devices.isEmpty()) {
                            statusLabel.setText("未找到任何蓝牙设备");
                            statusLabel.setCursor(Cursor.getDefaultCursor());
                        } else {
                            statusLabel.setText(
                                    "<html>未找到匹配设备，发现 <u style='color:#0066cc'>" + devices.size() + " 个设备</u></html>");
                            statusLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                            // 移除之前的监听器
                            for (java.awt.event.MouseListener l : statusLabel.getMouseListeners()) {
                                statusLabel.removeMouseListener(l);
                            }
                            // 添加点击事件
                            final List<BluetoothUtils.BluetoothDevice> foundDevices = devices;
                            statusLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                                @Override
                                public void mouseClicked(java.awt.event.MouseEvent e) {
                                    showDeviceListDialog(foundDevices);
                                }
                            });
                        }
                        statusLabel.setForeground(new Color(200, 80, 80));
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    // 停止超时定时器
                    if (timeoutTimer != null) {
                        timeoutTimer.stop();
                        timeoutTimer = null;
                    }
                    cancelScanning();
                    statusLabel.setText("扫描失败: " + e.getMessage());
                    statusLabel.setForeground(new Color(200, 80, 80));
                });
            }
        });
        scanThread.start();
    }

    /**
     * 显示发现的设备列表对话框
     */
    private void showDeviceListDialog(List<BluetoothUtils.BluetoothDevice> devices) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "发现的设备",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(350, 300);
        dialog.setLocationRelativeTo(this);

        JPanel mainPanel = new JPanel(new MigLayout("insets 15, fill, wrap 1", "[grow]"));
        mainPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("附近的蓝牙设备 (" + devices.size() + ")");
        titleLabel.setFont(UiUtils.FONT_BOLD.deriveFont(14f));
        mainPanel.add(titleLabel, "gapbottom 10");

        // 设备列表面板
        JPanel listPanel = new JPanel(new MigLayout("insets 0, wrap 1, gapy 5", "[grow]"));
        listPanel.setOpaque(false);

        for (BluetoothUtils.BluetoothDevice device : devices) {
            JPanel deviceRow = new JPanel(new MigLayout("insets 8, fillx", "[grow][]"));
            deviceRow.setBackground(new Color(248, 250, 255));
            deviceRow.setBorder(BorderFactory.createLineBorder(new Color(230, 235, 240)));

            // 设备名称和连接码+复制图标
            JPanel infoPanel = new JPanel(new MigLayout("insets 0, wrap 1, gapy 2", "[grow]"));
            infoPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(device.name);
            nameLabel.setFont(UiUtils.FONT_BOLD.deriveFont(12f));

            // 连接码 + 复制图标在同一行
            JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            codeRow.setOpaque(false);
            JLabel codeLabel = new JLabel(device.code);
            codeLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
            codeLabel.setForeground(Color.GRAY);
            JButton copyBtn = UiUtils.createCopyButton(device.code);
            codeRow.add(codeLabel);
            codeRow.add(copyBtn);

            infoPanel.add(nameLabel);
            infoPanel.add(codeRow);

            deviceRow.add(infoPanel, "growx");
            listPanel.add(deviceRow, "growx");
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, "grow, push");

        JButton closeBtn = new JButton("关闭");
        closeBtn.setBackground(UiUtils.COLOR_PRIMARY);
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> dialog.dispose());
        mainPanel.add(closeBtn, "right, w 80!");

        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }

    /**
     * 外部调用：收到连接通知时调用此方法
     */
    public void onIncomingConnection(String deviceName) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("收到连接: " + deviceName);
            statusLabel.setForeground(new Color(60, 160, 60));
        });
    }

    /**
     * 外部调用：连接失败通知
     */
    public void onConnectionFailed(String error) {
        SwingUtilities.invokeLater(() -> {
            // 停止超时定时器
            if (timeoutTimer != null) {
                timeoutTimer.stop();
                timeoutTimer = null;
            }

            // 恢复控件状态
            isScanning = false;
            if (scanThread != null) {
                scanThread.interrupt();
                scanThread = null;
            }

            scanBtn.setText("扫描连接");
            scanBtn.setEnabled(true);

            connectBtn.setText("连接他人");
            connectBtn.setForeground(UiUtils.COLOR_PRIMARY);
            connectBtn.setBorder(BorderFactory.createLineBorder(new Color(220, 230, 240), 2));
            for (java.awt.event.ActionListener l : connectBtn.getActionListeners()) {
                connectBtn.removeActionListener(l);
            }
            connectBtn.addActionListener(evt -> showConnectInput());

            codeInput.setEnabled(true);
            waitBtn.setEnabled(true);

            // 显示错误信息
            statusLabel.setText("连接失败: " + error);
            statusLabel.setForeground(new Color(200, 80, 80));

            revalidate();
            repaint();
        });
    }


    /**
     * 设置是否从设置页面进入
     * 
     * @param fromSettings true=从设置来，按钮显示"返回"；false=初次启动，显示"离线模式"
     */
    public void setFromSettings(boolean fromSettings) {
        if (fromSettings) {
            skipBtn.setText("返回聊天");
        } else {
            skipBtn.setText("离线模式");
        }
    }
}
