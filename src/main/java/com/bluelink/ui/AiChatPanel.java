package com.bluelink.ui;

import com.bluelink.ai.SpringAiService;
import com.bluelink.net.BluetoothSession;
import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.MarkdownUtils;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;
import reactor.core.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

/**
 * AI 聊天面板
 * 继承自 BaseChatPanel，保证与主聊天界面完全一致
 */
public class AiChatPanel extends BaseChatPanel {

    private static final Logger log = LoggerFactory.getLogger(AiChatPanel.class);

    private BluetoothSession session;
    private SpringAiService aiService;

    // AI 状态
    private boolean isAiResponding = false;
    private boolean isAiFromRemote = false;
    private StringBuilder currentAiResponse = new StringBuilder();
    private BubblePanel currentAiBubble;
    // 使用 JTextComponent 以兼容 JTextArea (普通文本) 和 JTextPane (HTML)
    private javax.swing.text.JTextComponent currentAiTextArea;
    
    // 节流控制：上次渲染时间
    private long lastRenderTime = 0;
    private static final long RENDER_INTERVAL = 200; // 200ms 刷新一次 Markdown

    // 会话控制
    private volatile String currentSessionId;
    private Disposable currentSubscription;
    private JComboBox<ConversationItem> sessionSelector;
    private String connectedDeviceName;
    private AtMentionManager atMentionManager;
    private boolean forceNewSession = false;

    public AiChatPanel() {
        super();
        this.atMentionManager = new AtMentionManager(inputArea);
        // Replace header title with combo box
        headerPanel.remove(headerLabel);
        
        // 优化 Header 布局
        headerPanel.setLayout(new MigLayout("insets 5 15 5 15, fill", "[grow][]", "[]"));
        
        sessionSelector = new JComboBox<>();
        sessionSelector.setFocusable(false); // Avoid stealing focus from input
        
        // 自定义渲染器：增加图标和间距
        sessionSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); // 增加内边距
                if (value instanceof ConversationItem) {
                    ConversationItem item = (ConversationItem) value;
                    if (item.id == null) {
                        label.setText(item.title); // 新建会话图标
                        label.setFont(label.getFont().deriveFont(Font.BOLD));
                        if (!isSelected) label.setForeground(UiUtils.COLOR_PRIMARY);
                    } else {
                        label.setText(item.title); // 普通会话图标
                    }
                }
                return label;
            }
        });
        
        sessionSelector.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                ConversationItem item = (ConversationItem) e.getItem();
                switchSession(item.id);
            }
        });
        
        headerPanel.add(sessionSelector, "growx, pushx, h 36!");
        
        // Add Refresh Button (Modern Style)
        JButton refreshBtn = new JButton("↻");
        refreshBtn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18));
        refreshBtn.setToolTipText("刷新会话列表");
        refreshBtn.setBorderPainted(false);
        refreshBtn.setContentAreaFilled(false);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.setForeground(Color.GRAY);
        
        // Hover effect
        refreshBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                refreshBtn.setForeground(UiUtils.COLOR_PRIMARY);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                refreshBtn.setForeground(Color.GRAY);
            }
        });

        refreshBtn.addActionListener(e -> refreshConversations());
        headerPanel.add(refreshBtn, "w 36!, h 36!"); 
        
        // Init with default
        refreshConversations();
    }
    
    private void switchSession(String sessionId) {
        // If switching to the same session, do nothing
        if (sessionId == null && currentSessionId == null) return;
        if (sessionId != null && sessionId.equals(currentSessionId)) return;
        
        // Multi-turn disabled check - REMOVED to allow local sessions
        // if (sessionId != null && !com.bluelink.util.AppConfig.isAiMultiTurnEnabled()) { ... }
        
        // Check for transitional state
        // REMOVED: Support mixed session types without clearing
        /*
        if (isTransitionalMode) {
            // ...
        }
        */
        
        this.currentSessionId = sessionId;
        if (sessionId == null) {
            forceNewSession = true;
        }
        
        // Clear UI
        chatArea.removeAll();
        chatArea.revalidate();
        chatArea.repaint();
        
        // Load History if not new chat
        if (sessionId != null && aiService != null) {
            java.util.List<org.springframework.ai.chat.messages.Message> history = aiService.getHistory(sessionId);
            if (history != null) {
                for (org.springframework.ai.chat.messages.Message msg : history) {
                    boolean isSelf = msg.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER;
                    if (isSelf) {
                        addTextBubble(true, msg.getText());
                    } else {
                        // AI 消息，尝试解析 Markdown
                        String text = msg.getText();
                        // 这里我们直接创建一个 HTML 气泡，而不是普通文本气泡
                        // 这样可以恢复图片的显示
                        try {
                            String html = MarkdownUtils.markdownToHtml(text);
                            // 使用专门的 HTML 气泡方法 (类似于 replaceBubbleWithMarkdown 里的逻辑)
                            addHtmlBubble(false, html);
                        } catch (Exception e) {
                            // Fallback
                            addTextBubble(false, text);
                        }
                    }
                }
                // Scroll to bottom
                SwingUtilities.invokeLater(() -> {
                     JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
                     vertical.setValue(vertical.getMaximum());
                });
            }
        }
    }

    // 新增：添加 HTML 气泡的方法
    private void addHtmlBubble(boolean isSender, String htmlContent) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        // 使用 HTML 气泡
        BubblePanel htmlBubble = BubbleFactory.createMarkdownBubble(isSender, htmlContent);
        
        // 样式设置
        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(htmlBubble, constraints);
        
        chatArea.add(wrapper, "growx, wrap");
        
        // 确保布局更新，特别是包含图片时
        chatArea.revalidate();
        chatArea.repaint();
    }

    public void setSession(BluetoothSession session) {
        this.session = session;
    }
    
    public void setConnectedDeviceName(String deviceName) {
        this.connectedDeviceName = deviceName;
        if (atMentionManager != null) {
            atMentionManager.setConnectedDeviceName(deviceName);
        }
    }

    public void setAiService(SpringAiService aiService) {
        this.aiService = aiService;
        refreshConversations();
    }
    
    // Flag to track if we are in a transitional state from Single-turn to Multi-turn
    // No longer used as we support mixed sessions
    // private boolean isTransitionalMode = false;

    public void onMultiTurnToggled(boolean enabled) {
        if (!enabled) {
            // Disabling Multi-turn (switching to Single-turn)
            // If the current session is a persistent Redis session, switch to New Chat.
            // This prevents the Redis session from lingering in the list or UI.
            if (currentSessionId != null && aiService != null && !aiService.isLocalSession(currentSessionId)) {
                 switchSession(null);
                 // CRITICAL: Clear the UI selection immediately so refreshConversations() doesn't
                 // try to "restore" the old session (which is about to be hidden).
                 sessionSelector.setSelectedItem(null);
            }
        }
        refreshConversations();
    }
    
    public void refreshUiState() {
        refreshConversations();
    }
    
    private void refreshConversations() {
        // Save current selection
        // CRITICAL FIX: 优先使用 currentSessionId，而不是下拉框的选中项
        // 下拉框选中项在新建会话时可能是 "New Chat" (null)，但实际上我们已经切换到了新 ID
        String selectedId = currentSessionId;
        
        // 只有当 currentSessionId 为 null 时，才尝试从下拉框获取 (虽然理论上这也只会得到 null)
        if (selectedId == null) {
            ConversationItem selectedItem = (ConversationItem) sessionSelector.getSelectedItem();
            if (selectedItem != null) selectedId = selectedItem.id;
        }

        // 暂时移除监听器，防止在添加 Item 时触发 ItemStateChanged
        java.awt.event.ItemListener[] listeners = sessionSelector.getItemListeners();
        for (java.awt.event.ItemListener l : listeners) {
            sessionSelector.removeItemListener(l);
        }

        sessionSelector.removeAllItems();
        
        // Add "New Chat"
        sessionSelector.addItem(new ConversationItem(null, "New Chat (新建会话)"));
        
        // Only load history if multi-turn is enabled OR if we have local sessions
        boolean isMultiTurn = com.bluelink.util.AppConfig.isAiMultiTurnEnabled();
        
        // Allow session selector if multi-turn is enabled OR if we have any conversations (local or remote)
        // Actually, we want to enable it always now to support creating new local sessions?
        // But if user hasn't created any, it should probably be enabled so they CAN create one (via New Chat + send)
        // Let's enable it always.
        sessionSelector.setEnabled(true); 
        
        if (aiService != null) {
            java.util.Map<String, String> conversations = aiService.getConversations();
            for (java.util.Map.Entry<String, String> entry : conversations.entrySet()) {
                String id = entry.getKey();
                if (id != null && id.startsWith("mainchat:")) {
                    continue;
                }
                sessionSelector.addItem(new ConversationItem(id, entry.getValue()));
            }
        }
        
        // Restore selection
        boolean found = false;
        if (selectedId != null) {
            for (int i = 0; i < sessionSelector.getItemCount(); i++) {
                ConversationItem item = sessionSelector.getItemAt(i);
                if (selectedId.equals(item.id)) {
                    sessionSelector.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
        }
        
        if (!found) {
            // 如果没找到 (可能是新会话还没来得及保存到 map 里，或者确实是新会话，或者是切换模式后被过滤的会话)
            // 如果 currentSessionId 不为空，我们应该临时加进去，否则用户会觉得界面跳变
            if (selectedId != null && !selectedId.isEmpty()) {
                // 尝试获取标题
                String title = "Current Chat";
                if (aiService != null) {
                    // 使用新方法尝试获取标题，无论当前模式如何
                    String realTitle = aiService.getConversationTitle(selectedId);
                    if (realTitle != null) {
                        title = realTitle;
                    } else {
                        // Fallback: Check standard map (redundant but safe)
                        Map<String, String> map = aiService.getConversations();
                        if (map.containsKey(selectedId)) {
                            title = map.get(selectedId);
                        }
                    }
                }
                
                // Double check if it really isn't in the list (paranoid check against duplicates)
                boolean alreadyExists = false;
                for (int i = 0; i < sessionSelector.getItemCount(); i++) {
                     ConversationItem item = sessionSelector.getItemAt(i);
                     if (selectedId.equals(item.id)) {
                         sessionSelector.setSelectedIndex(i);
                         alreadyExists = true;
                         break;
                     }
                }
                
                if (!alreadyExists) {
                    ConversationItem newItem = new ConversationItem(selectedId, title);
                    sessionSelector.addItem(newItem);
                    sessionSelector.setSelectedItem(newItem);
                }
            } else {
                sessionSelector.setSelectedIndex(0); // Select New Chat
            }
        }
        
        // 恢复监听器
        for (java.awt.event.ItemListener l : listeners) {
            sessionSelector.addItemListener(l);
        }
    }

    @Override
    protected void performSendAction() {
        if (isAiResponding) {
            stopGeneration();
            return;
        }
        if (atMentionManager != null && atMentionManager.isPopupVisible()) {
            if (atMentionManager.confirmSelection()) {
                return;
            }
        } else {
            super.performSendAction();
        }
    }

    private void stopGeneration() {
        if (isAiFromRemote) {
            if (session != null) {
                try {
                    session.sendAiStop();
                } catch (Exception e) {
                    addTextBubble(false, "暂停失败: " + e.getMessage());
                }
            }
        } else {
            if (currentSubscription != null && !currentSubscription.isDisposed()) {
                currentSubscription.dispose();
                log.info("AI generation stopped by user.");
            }
        }
        appendAiChunk("\n\n[用户终止输出]");
        onAiComplete();
    }

    @Override
    protected void onSend(String text) {
        String mention = AtMentionManager.extractRawMention(text);
        boolean isRemoteMention = mention != null && connectedDeviceName != null && connectedDeviceName.equals(mention);
        String remotePrompt = null;
        if (isRemoteMention) {
            remotePrompt = AtMentionManager.stripMention(text);
            if (remotePrompt == null || remotePrompt.isEmpty()) {
                addTextBubble(true, text);
                clearInput();
                return;
            }
        }
        
        // 1. 显示用户已发送
        addTextBubble(true, text);
        clearInput();
        
        if (isRemoteMention) {
            handleRemoteAiRequest(remotePrompt);
            return;
        }

        // 2. 发送请求 (优先使用 Spring AI)
        if (aiService != null) {
            try {
                // 停止上一次未完成的请求
                if (currentSubscription != null && !currentSubscription.isDisposed()) {
                    currentSubscription.dispose();
                }
                // 如果 UI 还在显示“正在输入”，强制结束
                if (isAiResponding) {
                    onAiComplete();
                }

                // 生成或复用会话 ID
                String sessionId;
                
                // Logic: If currentSessionId is set (from dropdown), use it.
                // If it is null (New Chat), generate one AND update the dropdown to reflect it.
                if (this.currentSessionId == null || forceNewSession) {
                     this.currentSessionId = UUID.randomUUID().toString();
                     forceNewSession = false;
                     // CRITICAL FIX: 立即更新下拉框选中状态，防止后续 refreshConversations 找不到选中项
                     // 虽然此时下拉框里还没有这个 ID，但我们可以利用 selectedId 变量在 refresh 时优先匹配
                }
                sessionId = this.currentSessionId;
                
                log.debug("DEBUG: [AI Start] Sending request... SessionId={} (Multi-turn: {})", sessionId, com.bluelink.util.AppConfig.isAiMultiTurnEnabled());

                appendAiMessageStart(currentSessionId, false);
                
                // 这里的 streamChat 需要适配 SpringAiService 的新接口 (带 sessionId)
                currentSubscription = aiService.streamChat(text, sessionId)
                        .subscribe(
                                chunk -> {
                                    if (sessionId.equals(currentSessionId)) {
                                        // 直接输出真实内容到控制台，以便调试性能
//                                        log.debug("DEBUG: [AI Chunk] {}", chunk);
                                        appendAiChunk(chunk);
                                    }
                                },
                                error -> {
                                    log.error("AI Error", error);
                                    if (sessionId.equals(currentSessionId)) {
                                        appendAiChunk("\n[Error: " + error.getMessage() + "]");
                                        onAiComplete();
                                    }
                                },
                                () -> {
                                    if (sessionId.equals(currentSessionId)) {
                                        onAiComplete();
                                        // Refresh conversations list after completion to show title if it was new
                                        SwingUtilities.invokeLater(this::refreshConversations);
                                    }
                                }
                        );

            } catch (Exception e) {
                log.error("Failed to start AI chat", e);
                addTextBubble(false, "Error: " + e.getMessage());
            }
        } else {
            addTextBubble(false, "AI Service not available");
        }
    }
    
    private void handleRemoteAiRequest(String prompt) {
        if (session == null) {
            addTextBubble(false, "未连接，无法调用对方 AI");
            return;
        }
        
        if (this.currentSessionId == null || forceNewSession) {
            this.currentSessionId = UUID.randomUUID().toString();
            forceNewSession = false;
        }
        
        String label = connectedDeviceName != null ? connectedDeviceName : "AI";
        appendAiMessageStart(label, true);
        
        final String promptToSend;
        if (aiService != null) {
            aiService.recordConversationTitle(currentSessionId, prompt);
            promptToSend = aiService.buildRemotePrompt(currentSessionId, prompt);
            aiService.appendLocalUserMessage(currentSessionId, prompt);
        } else {
            promptToSend = prompt;
        }
        
        new Thread(() -> {
            try {
                session.sendAiRequest(promptToSend);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    addTextBubble(false, "AI 请求失败: " + e.getMessage());
                    onAiComplete();
                });
            }
        }).start();
    }
    
    private static class ConversationItem {
        String id;
        String title;
        
        public ConversationItem(String id, String title) {
            this.id = id;
            this.title = title;
        }
        
        @Override
        public String toString() {
            return title;
        }
    }

    public void appendAiMessageStart() {
        appendAiMessageStart(currentSessionId, false);
    }
    
    public void appendAiMessageStart(String label, boolean fromRemote) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendAiMessageStartInternal(label, fromRemote);
        } else {
            SwingUtilities.invokeLater(() -> appendAiMessageStartInternal(label, fromRemote));
        }
    }

    // 记录上一次的高度，用于优化 revalidate
    private int lastBubbleHeight = -1;

    private void appendAiMessageStartInternal(String label, boolean fromRemote) {
        // 这里的逻辑修改为：总是开始新的，如果旧的没结束，onSend 已经处理了
        isAiResponding = true;
        isAiFromRemote = fromRemote;
        
        // Update Send Button to Stop Button
        sendButton.setText("停止");
        sendButton.setBackground(new Color(220, 53, 69)); // Danger Red
        
        currentAiResponse.setLength(0);
        lastBubbleHeight = -1;
        lastRenderTime = 0;

        // 创建初始空气泡 "Thinking..."
        String bubbleLabel = (label == null || label.isEmpty()) ? "AI" : label;
        currentAiBubble = BubbleFactory.createAiThinkingBubble(bubbleLabel);
        
        // 添加到界面 (使用 addBubble 逻辑)
        // addTextBubble 封装了 wrapper 创建，这里我们需要手动添加或者修改 addTextBubble
        // 简单起见，我们复用 addTextBubble 的 wrapper 逻辑，但这里因为是 specialized bubble，
        // 我们手动添加
        
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);
        String constraints = "al left, width ::80%";
        wrapper.add(currentAiBubble, constraints);
        chatArea.add(wrapper, "growx, wrap");
        
        chatArea.revalidate();
        chatArea.repaint();
        scrollToBottom();

        // 获取内部 TextArea 引用 (可能是 AdaptiveTextArea 或 JTextPane)
        currentAiTextArea = findTextComponent(currentAiBubble);
    }
    
    private javax.swing.text.JTextComponent findTextComponent(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof javax.swing.text.JTextComponent) {
                return (javax.swing.text.JTextComponent) c;
            }
            if (c instanceof Container) {
                javax.swing.text.JTextComponent found = findTextComponent((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    // 保留旧方法以兼容 (虽然现在不常用了)
    private JTextArea findTextArea(Container container) {
        javax.swing.text.JTextComponent c = findTextComponent(container);
        if (c instanceof JTextArea) return (JTextArea) c;
        return null;
    }

    public void appendAiChunk(String chunk) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendAiChunk(chunk));
            return;
        }
        
        if (!isAiResponding) return;

        // CRITICAL FIX: 如果是第一个 chunk，且内容为空，则忽略
        // 避免 SpringAI 可能发送一个空 chunk 作为 start 信号，导致 "Thinking..." 被清空但没有新内容
        if (currentAiResponse.length() == 0 && (chunk == null || chunk.isEmpty())) {
            return;
        }

        boolean isFirstChunk = false;
        // 如果是第一个 chunk (且非空)，清空 "Thinking..."
        if (currentAiResponse.length() == 0 && currentAiTextArea != null) {
            currentAiTextArea.setText("");
            isFirstChunk = true;
        }

        currentAiResponse.append(chunk);

        if (currentAiTextArea != null) {
            long now = System.currentTimeMillis();
            
            // 判断是否是 HTML 模式 (JTextPane)
            boolean isHtmlMode = (currentAiTextArea instanceof JTextPane);
            
            if (isHtmlMode) {
                // 流式 Markdown 渲染：节流刷新
                // 只有当距离上次刷新超过间隔，或者这是第一帧/最后阶段，才进行 Markdown 转换
                // 注意：实时 Markdown 渲染可能会很重，所以必须节流
                if (isFirstChunk || (now - lastRenderTime > RENDER_INTERVAL)) {
                    // CRITICAL FIX: 流式渲染时，如果 Markdown 不完整（例如图片标签截断），渲染器可能无法正确输出 img 标签
                    // 但通常流式追加是按字符的，图片链接是一串字符。
                    // 只要链接完整了，渲染就应该出来。
                    String html = MarkdownUtils.markdownToHtml(currentAiResponse.toString());
                    
                    // 强制异步加载图片 (Swing HTMLEditorKit 默认同步加载网络图片可能会卡顿，或者根本不加载)
                    // 但 JTextPane 的 ImageCacher 是自动的。
                    // 重点：不要每次都 setText，这会重置 Scroll 和 图片加载状态。
                    // 但流式必须 setText。
                    
                    // 只有当 HTML 结构确实变化很大时才更新？不，必须实时。
                    // 问题在于：setText 会导致之前的图片重新加载，如果频率太高（200ms），
                    // 图片可能永远处于“加载中”或者“失败”状态，导致显示框框。
                    // 只有当 AI 完成时 (onAiComplete)，我们做最后一次 setText。
                    // 用户反馈“流式的时候还能看见”，说明 200ms 的间隔足够加载图片（或者图片在本地缓存了）。
                    // 但是“完成后变成方框”，说明 onAiComplete 里的逻辑有问题。
                    
                    currentAiTextArea.setText(html);
                    // 必须手动移动 Caret 到末尾，否则滚动条会跳回顶部
                    currentAiTextArea.setCaretPosition(currentAiTextArea.getDocument().getLength());
                    lastRenderTime = now;
                }
                // 如果在节流期间，我们不做任何 UI 更新，只是积累数据
                // 但为了让用户感觉在动，也许可以 append 原始文本？不行，格式会乱。
                // 只能等待节流周期。
            } else if (currentAiTextArea instanceof JTextArea) {
                // 普通文本模式，直接追加
                ((JTextArea) currentAiTextArea).append(chunk);
            }
            
            // 优化：总是重绘当前气泡以显示新文本
            currentAiBubble.repaint();
            
            // 只有当高度发生变化时，才触发全局 revalidate 和滚动
            // 这样可以避免每次字符追加都导致整个列表重排
            int newHeight = currentAiBubble.getPreferredSize().height;
            
            // CRITICAL FIX: 如果是第一个 chunk，或者高度变化，必须 revalidate
            // 原因是 "Thinking..." 变成 "" 再变成 "Text" 会导致宽度剧烈变化，如果不 revalidate，
            // MigLayout 可能不会重新分配宽度，导致内容被截断或不可见
            if (isFirstChunk || newHeight != lastBubbleHeight) {
                lastBubbleHeight = newHeight;
                chatArea.revalidate();
                chatArea.repaint(); 
                scrollToBottom();
            }
        }
    }

    public void onAiComplete() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onAiComplete);
            return;
        }
        
        // Reset Send Button
        sendButton.setText("发送");
        sendButton.setBackground(UiUtils.COLOR_PRIMARY);

        // 最终完成时，强制刷新一次 Markdown 以确保显示完整
        if (isAiResponding && currentAiTextArea != null && currentAiResponse.length() > 0) {
            // 如果是 HTML 模式，直接更新当前 bubble 的内容即可，不需要销毁重建
            // 这样可以避免闪烁
             if (currentAiTextArea instanceof JTextPane) {
                String rawMarkdown = currentAiResponse.toString();
                String html = MarkdownUtils.markdownToHtml(rawMarkdown);
                String currentText = currentAiTextArea.getText();
                try {
                    currentAiTextArea.setText(html);
                    currentAiTextArea.setCaretPosition(currentAiTextArea.getDocument().getLength());
                } catch (Exception e) {
                    log.error("Failed to set final HTML", e);
                }
             } else {
                 // 如果是旧的普通文本模式，才执行替换逻辑 (fallback)
                 replaceBubbleWithMarkdown();
             }
        }

        if (isAiFromRemote && aiService != null && currentSessionId != null && currentAiResponse.length() > 0) {
            aiService.appendLocalAssistantMessage(currentSessionId, currentAiResponse.toString());
            SwingUtilities.invokeLater(this::refreshConversations);
        }
        
        isAiResponding = false;
        isAiFromRemote = false;
        currentAiBubble = null;
        currentAiTextArea = null;
        currentSubscription = null;
        
        // 最后滚动到底部
        scrollToBottom();
    }
    
    private void replaceBubbleWithMarkdown() {
        if (currentAiBubble == null) return;
        try {
            // 1. 获取 Markdown 文本
            String rawMarkdown = currentAiResponse.toString();
            
            // 2. 转换为 HTML
            String html = MarkdownUtils.markdownToHtml(rawMarkdown);
            
            // 3. 获取当前气泡在 chatArea 中的位置
            int index = -1;
            Component[] comps = chatArea.getComponents();
            for (int i = 0; i < comps.length; i++) {
                // addTextBubble 实际上添加的是一个 wrapper (JPanel)，wrapper 包含 BubblePanel
                if (comps[i] instanceof JPanel) {
                    JPanel wrapper = (JPanel) comps[i];
                    if (wrapper.isAncestorOf(currentAiBubble)) {
                        index = i;
                        break;
                    }
                }
            }
            
            // 4. 如果找到了位置，移除旧的纯文本气泡，插入新的 HTML 气泡
            if (index != -1) {
                chatArea.remove(index);
                
                // 创建新的 wrapper 和 bubble (参考 BaseChatPanel.addTextBubble)
                JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
                wrapper.setOpaque(false);

                // 使用 HTML 气泡
                BubblePanel htmlBubble = BubbleFactory.createMarkdownBubble(false, html);
                
                // 接收者样式
                String constraints = "al left, width ::80%";
                wrapper.add(htmlBubble, constraints);
                
                chatArea.add(wrapper, "growx, wrap", index);
                
                chatArea.revalidate();
                chatArea.repaint();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 兼容接口
    public void onAiStreamChunk(String chunk) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onAiStreamChunk(chunk));
            return;
        }
        if (!isAiResponding) {
            String label = connectedDeviceName != null ? connectedDeviceName : "AI";
            appendAiMessageStart(label, true);
        }
        appendAiChunk(chunk);
    }
    
    public void onAiStreamComplete() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onAiStreamComplete);
            return;
        }
        if (isAiFromRemote) {
            onAiComplete();
        }
    }
    
    public boolean isRemoteAiResponding() {
        return isAiResponding && isAiFromRemote;
    }
}
