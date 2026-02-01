package com.bluelink.ui;

import com.bluelink.net.BluetoothClient;
import com.bluelink.db.TransferDao;
import com.bluelink.net.BluetoothSession;
import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.UiUtils;

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

    public MainChatPanel(ModernQQFrame parentFrame) {
        super();
        this.parentFrame = parentFrame;
        setHeaderTitle("未连接");

        // 核心功能初始化
        setupInputExtensions(); // 粘贴、拖拽等
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

    public void loadHistory() {
        if (hasLoadedAllHistory)
            return;

        java.util.List<TransferDao.LogItem> list = TransferDao.loadHistory(-1, 25);
        if (list.isEmpty()) {
            hasLoadedAllHistory = true;
            addSystemTip("- 已显示全部历史消息 -");
            return;
        }

        // Reverse order for insert at top
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

        scrollToBottom();
    }
}
