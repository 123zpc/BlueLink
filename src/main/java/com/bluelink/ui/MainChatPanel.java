package com.bluelink.ui;

import com.bluelink.net.BluetoothClient;
import com.bluelink.db.TransferDao;
import com.bluelink.net.BluetoothSession;
import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主聊天面板 (MainChatPanel)
 * 继承自 BaseChatPanel，实现具体的业务逻辑：
 * 1. 文件/图片粘贴与发送
 * 2. 数据库历史记录加载
 * 3. 蓝牙/网络消息发送
 * 4. 气泡重试逻辑
 */
public class MainChatPanel extends BaseChatPanel {

    private final ModernQQFrame parentFrame;
    private BluetoothSession currentSession;
    private BluetoothClient client;
    private final ExecutorService fileSendExecutor = Executors.newSingleThreadExecutor();

    // 记录 ID 状态
    private long minLoadedId = Long.MAX_VALUE;
    private boolean hasLoadedAllHistory = false;
    private boolean isLoadingHistory = false;

    // File Transfer State
    private final java.util.Map<String, BubblePanel> activeFileBubbles = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BubblePanel> sendingFileBubbles = new java.util.concurrent.ConcurrentHashMap<>();

    // 监听器引用，用于在首次加载后再添加
    private java.awt.event.AdjustmentListener scrollListener;
    private JPanel loadingPanel;

    public MainChatPanel(ModernQQFrame parentFrame) {
        super();
        this.parentFrame = parentFrame;
        setHeaderTitle("未连接");

        // 核心功能初始化
        setupInputExtensions(); // 粘贴、拖拽等
        
        // 初始化滚动监听器，但暂不添加到 ScrollBar
        scrollListener = e -> {
            if (!e.getValueIsAdjusting() && e.getValue() == 0) {
                // 仅当有可滚动内容时才加载历史 (避免初始化时重复加载)
                JScrollBar vBar = chatScrollPane.getVerticalScrollBar();
                if (vBar.getMaximum() > vBar.getVisibleAmount()) {
                    loadHistory();
                }
            }
        };
    }

    public void setSession(BluetoothSession session) {
        this.currentSession = session;
    }

    public void setClient(BluetoothClient client) {
        this.client = client;
    }

    @Override
    protected void onSend(String text) {
        if (text.startsWith("/connect ")) {
            // Handle command
            String addr = text.substring(9).trim();
            if (client != null)
                client.connect(addr);
            clearInput();
            return;
        }

        // 1. Save DB
        TransferDao.LogItem item = new TransferDao.LogItem("TEXT", true, text, 0);
        item.status = "SENDING";
        TransferDao.save(item);

        // 2. UI Render
        BubblePanel bubble = addTextBubble(true, text); // Base method
        clearInput();

        // 3. Async Send
        new Thread(() -> {
            try {
                if (currentSession != null) {
                    currentSession.sendMessage(text);
                } else if (client != null) {
                    client.send(text);
                }

                if (item.id > 0) {
                    TransferDao.updateStatus(item.id, "SUCCESS");
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (bubble != null) {
                        bubble.setStatus(true);
                        bubble.setRetryAction(() -> performResend(item, bubble));
                    }
                });
                if (item.id > 0)
                    TransferDao.updateStatus(item.id, "FAILED");
            }
        }).start();
    }

    // --- File & Paste Logic ---

    private void setupInputExtensions() {
        // 拦截粘贴操作 (Ctrl+V) Logic copied from ModernQQFrame
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ctrl V"), "paste-check");
        inputArea.getActionMap().put("paste-check", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handlePaste();
            }
        });
    }

    private void handlePaste() {
        try {
            java.awt.datatransfer.Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null)
                return;

            // 1. Files
            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                java.util.List<File> files = (java.util.List<File>) t
                        .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                for (File file : files)
                    performFileSend(file);
                return;
            }

            // 2. Images (Placeholder / Simplified)
            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                // If we want to support image paste, we can add it here.
                return;
            }

            // 3. Text
            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                inputArea.paste(); // Default
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void performFileSend(File file) {
        String taskKey = java.util.UUID.randomUUID().toString();

        SwingUtilities.invokeLater(() -> {
            BubblePanel bubble = addFileBubble(true, file);
            sendingFileBubbles.put(taskKey, bubble);

            fileSendExecutor.submit(() -> {
                TransferDao.LogItem item = new TransferDao.LogItem("FILE", true, file.getAbsolutePath(), file.length());
                item.status = "SENDING";
                TransferDao.save(item);

                try {
                    if (currentSession != null)
                        currentSession.sendFile(file, taskKey);
                    else if (client != null)
                        client.sendFile(file);

                    if (item.id > 0)
                        TransferDao.updateStatus(item.id, "SUCCESS");
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        bubble.setStatus(true);
                        bubble.setRetryAction(() -> performResend(item, bubble));
                    });
                }
            });
        });
    }

    public void onFileReceived(String originalName, File file) {
        SwingUtilities.invokeLater(() -> {
            // 1. Remove progress bubble
            BubblePanel bubble = activeFileBubbles.remove(originalName);
            if (bubble != null) {
                Container wrapper = bubble.getParent();
                if (wrapper != null)
                    chatArea.remove(wrapper);
            }

            // 2. Add final bubble
            addFileBubble(false, file);

            // 3. Refresh
            chatArea.revalidate();
            chatArea.repaint();
            scrollToBottom();
        });
    }

    public void onTransferProgress(String fileName, long current, long total, boolean isReceive) {
        SwingUtilities.invokeLater(() -> {
            BubblePanel bubble;
            if (isReceive) {
                bubble = activeFileBubbles.get(fileName);
                if (bubble == null) {
                    bubble = renderReceivingFileBubble(fileName);
                    activeFileBubbles.put(fileName, bubble);
                }
                String progressText = String.format("接收中 %s / %s",
                        BubbleFactory.formatSize(current), BubbleFactory.formatSize(total));
                BubbleFactory.updateBubbleSizeText(bubble, progressText);
            } else {
                bubble = sendingFileBubbles.get(fileName);
                if (bubble != null) {
                    String progressText = String.format("发送中 %s / %s",
                            BubbleFactory.formatSize(current), BubbleFactory.formatSize(total));
                    BubbleFactory.updateBubbleSizeText(bubble, progressText);
                }
            }

            if (bubble != null) {
                float p = (float) current / total;
                p = Math.max(0f, Math.min(1f, p));
                bubble.setProgress(p);

                if (!isReceive && current >= total) {
                    sendingFileBubbles.remove(fileName);
                    BubbleFactory.updateBubbleSizeText(bubble, BubbleFactory.formatSize(total));
                    bubble.setProgress(-1f);
                }
            }
        });
    }

    private BubblePanel renderReceivingFileBubble(String fileName) {
        // Custom rendering for receiving bubble (Left Aligned)
        // Similar to addTextBubble but specific constraints
        BubblePanel bubble = BubbleFactory.createReceivingFileBubble(fileName);
        bubble.setProgress(0f);

        JPanel wrapper = new JPanel(new net.miginfocom.swing.MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(bubble, "al left, width ::80%");

        chatArea.add(wrapper, "growx, wrap");
        scrollToBottom();

        return bubble;
    }

    private void performResend(TransferDao.LogItem item, BubblePanel bubble) {
        bubble.setStatus(false);
        new Thread(() -> {
            try {
                if ("TEXT".equals(item.type)) {
                    if (currentSession != null)
                        currentSession.sendMessage(item.content);
                } else {
                    if (currentSession != null)
                        currentSession.sendFile(new File(item.content), null);
                }
                if (item.id > 0)
                    TransferDao.updateStatus(item.id, "SUCCESS");
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> bubble.setStatus(true));
                if (item.id > 0)
                    TransferDao.updateStatus(item.id, "FAILED");
            }
        }).start();
    }

    /**
     * 初始化历史记录 (仅当从未加载过时执行)
     * 避免外部重复调用导致自动加载下一页
     */
    public void initHistory() {
        if (minLoadedId == Long.MAX_VALUE) {
            loadHistory();
        }
    }

    private JPanel getLoadingPanel() {
        if (loadingPanel == null) {
            JLabel label = new JLabel("加载中...");
            label.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
            label.setForeground(Color.GRAY);
            label.setHorizontalAlignment(SwingConstants.CENTER);

            loadingPanel = new JPanel(new MigLayout("insets 5, fillx, alignx center", "[center]", "[]"));
            loadingPanel.setOpaque(false);
            loadingPanel.add(label);
        }
        return loadingPanel;
    }

    public void loadHistory() {
        if (hasLoadedAllHistory || isLoadingHistory)
            return;

        isLoadingHistory = true;

        // 记录当前滚动位置和高度
        JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
        int oldHeight = vertical.getMaximum();

        // 如果是首次加载 (minLoadedId == Long.MAX_VALUE)，使用 -1 查询最新
        long queryId = (minLoadedId == Long.MAX_VALUE) ? -1 : minLoadedId;

        // 首次加载为了极速体验，直接在当前线程(EDT)执行 (本地DB通常很快 <10ms)
        // 这样可以避免异步造成的闪烁和空白等待
        if (queryId == -1) {
            try {
                java.util.List<TransferDao.LogItem> list = TransferDao.loadHistory(queryId, 20);
                updateHistoryUI(list, queryId, vertical, oldHeight);
            } catch (Exception e) {
                e.printStackTrace();
                isLoadingHistory = false;
            }
            return;
        }

        // 历史分页加载显示 Loading，并使用异步线程
        JPanel loading = getLoadingPanel();
        chatArea.add(loading, "growx, wrap", 0);
        chatArea.revalidate();
        chatArea.repaint();

        new Thread(() -> {
            java.util.List<TransferDao.LogItem> list = TransferDao.loadHistory(queryId, 20);
            SwingUtilities.invokeLater(() -> {
                chatArea.remove(loading);
                updateHistoryUI(list, queryId, vertical, oldHeight);
            });
        }).start();
    }

    private void updateHistoryUI(java.util.List<TransferDao.LogItem> list, long queryId, JScrollBar vertical, int oldHeight) {
        if (list.isEmpty()) {
            hasLoadedAllHistory = true;
            insertSystemTipAt(0, "--- 已显示全部历史消息 ---");
            isLoadingHistory = false;
            chatArea.revalidate();
            chatArea.repaint();
            return;
        }

        // 倒序插入到顶部
        for (int i = list.size() - 1; i >= 0; i--) {
            TransferDao.LogItem item = list.get(i);
            if ("TEXT".equals(item.type)) {
                insertTextBubbleAt(0, item.isSender, item.content);
            } else {
                insertFileBubbleAt(0, item.isSender, new File(item.content));
            }
            if (item.id < minLoadedId)
                minLoadedId = item.id;
        }

        // 刷新布局
        chatArea.revalidate();

        // 恢复滚动位置
        // 始终使用 invokeLater 确保在 Swing 渲染周期中执行布局更新后的逻辑
        SwingUtilities.invokeLater(() -> {
            // 关键：强制让 ScrollPane 更新布局状态，确保 getMaximum 获取到最新值
            // revalidate 只是标记无效，validate 才会立即触发布局计算
            chatArea.validate(); 
            chatScrollPane.validate();
            
            JScrollBar vBar = chatScrollPane.getVerticalScrollBar();

            if (queryId == -1) {
                // 首次加载，滚动到底部
                vBar.setValue(vBar.getMaximum());
                
                // 延迟添加监听器，防止 setValue 触发的 AdjustmentEvent 导致循环
                // 再次 invokeLater 确保在滚动动作完成后才挂载监听
                SwingUtilities.invokeLater(() -> {
                    vBar.removeAdjustmentListener(scrollListener);
                    vBar.addAdjustmentListener(scrollListener);
                });
            } else {
                // 历史加载，保持视觉位置不变
                int newHeight = vertical.getMaximum();
                vertical.setValue(newHeight - oldHeight);
            }
            isLoadingHistory = false;
        });
    }
}
