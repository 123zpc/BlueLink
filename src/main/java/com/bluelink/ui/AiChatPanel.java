package com.bluelink.ui;

import com.bluelink.ai.SpringAiService;
import com.bluelink.net.BluetoothSession;
import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.MarkdownUtils;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;
import reactor.core.Disposable;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

/**
 * AI 聊天面板
 * 继承自 BaseChatPanel，保证与主聊天界面完全一致
 */
public class AiChatPanel extends BaseChatPanel {

    private BluetoothSession session;
    private SpringAiService aiService;

    // AI 状态
    private boolean isAiResponding = false;
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

    public AiChatPanel() {
        super();
        setHeaderTitle("AI 助手");
        // 初始化时添加一条欢迎语? 或者保持空
        // addSystemTip("--------- 已显示全部消息 ---------");
    }

    public void setSession(BluetoothSession session) {
        this.session = session;
    }

    public void setAiService(SpringAiService aiService) {
        this.aiService = aiService;
    }

    @Override
    protected void onSend(String text) {
        // 1. 显示用户已发送
        addTextBubble(true, text);
        clearInput();

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

                // 生成新会话 ID
                final String sessionId = UUID.randomUUID().toString();
                this.currentSessionId = sessionId;
                
                System.out.println("DEBUG: [AI Start] Sending request... SessionId=" + sessionId);

                appendAiMessageStart();
                
                currentSubscription = aiService.streamChat(text)
                        .subscribe(
                                chunk -> {
                                    if (sessionId.equals(currentSessionId)) {
                                        // 直接输出真实内容到控制台，以便调试性能
//                                        System.out.print(chunk);
                                        appendAiChunk(chunk);
                                    }
                                },
                                error -> {
                                    if (sessionId.equals(currentSessionId)) {
                                        System.out.println("\nDEBUG: [AI Error] " + error.getMessage());
                                        appendAiChunk("\n[Error: " + error.getMessage() + "]");
                                        onAiComplete();
                                    }
                                },
                                () -> {
                                    if (sessionId.equals(currentSessionId)) {
                                        System.out.println("\nDEBUG: [AI Complete]");
                                        onAiComplete();
                                    }
                                }
                        );
            } catch (Exception ex) {
                addSystemTip("AI 服务错误: " + ex.getMessage());
                onAiComplete();
            }
        } else if (session != null && !session.isClosed()) {
            // 降级到旧的蓝牙发送 (如果需要的话，但用户要求暂时不发送)
            // 用户要求：暂时不实现将内容通过蓝牙发送到另一台
            addSystemTip("AI 服务未初始化，且蓝牙发送已禁用");
        } else {
            addSystemTip("错误: AI 服务不可用");
        }
    }

    public void appendAiMessageStart() {
        if (SwingUtilities.isEventDispatchThread()) {
            appendAiMessageStartInternal();
        } else {
            SwingUtilities.invokeLater(this::appendAiMessageStartInternal);
        }
    }

    // 记录上一次的高度，用于优化 revalidate
    private int lastBubbleHeight = -1;

    private void appendAiMessageStartInternal() {
        // 这里的逻辑修改为：总是开始新的，如果旧的没结束，onSend 已经处理了
        isAiResponding = true;
        currentAiResponse.setLength(0);
        lastBubbleHeight = -1;
        lastRenderTime = 0;

        // 创建初始空气泡 "Thinking..."
        currentAiBubble = BubbleFactory.createAiThinkingBubble(currentSessionId);
        
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
        
        // 移除 isAiResponding 检查和自动开始，因为现在完全由 onSend 控制
        // 如果当前没有响应状态（例如已被重置），则丢弃 chunk
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
                    String html = MarkdownUtils.markdownToHtml(currentAiResponse.toString());
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

        // 最终完成时，强制刷新一次 Markdown 以确保显示完整
        if (isAiResponding && currentAiTextArea != null && currentAiResponse.length() > 0) {
            // 如果是 HTML 模式，直接更新当前 bubble 的内容即可，不需要销毁重建
            // 这样可以避免闪烁
             if (currentAiTextArea instanceof JTextPane) {
                String rawMarkdown = currentAiResponse.toString();
                String html = MarkdownUtils.markdownToHtml(rawMarkdown);
                currentAiTextArea.setText(html);
                currentAiTextArea.setCaretPosition(currentAiTextArea.getDocument().getLength());
             } else {
                 // 如果是旧的普通文本模式，才执行替换逻辑 (fallback)
                 replaceBubbleWithMarkdown();
             }
        }

        isAiResponding = false;
        currentAiBubble = null;
        currentAiTextArea = null;
        
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
        appendAiChunk(chunk);
    }
}
