package com.bluelink.ui;

import com.bluelink.net.BluetoothSession;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.UiUtils;

import javax.swing.*;
import java.awt.*;

/**
 * AI 聊天面板
 * 继承自 BaseChatPanel，保证与主聊天界面完全一致
 */
public class AiChatPanel extends BaseChatPanel {

    private BluetoothSession session;

    // AI 状态
    private boolean isAiResponding = false;
    private StringBuilder currentAiResponse = new StringBuilder();
    private BubblePanel currentAiBubble;
    private JTextArea currentAiTextArea;

    public AiChatPanel() {
        super();
        setHeaderTitle("AI 助手");
        // 初始化时添加一条欢迎语? 或者保持空
        // addSystemTip("--------- 已显示全部消息 ---------");
    }

    public void setSession(BluetoothSession session) {
        this.session = session;
    }

    @Override
    protected void onSend(String text) {
        // 1. 显示用户已发送
        addTextBubble(true, text);
        clearInput();

        // 2. 发送请求
        if (session != null && !session.isClosed()) {
            try {
                session.sendAiRequest(text);
                appendAiMessageStart();
            } catch (Exception ex) {
                addSystemTip("发送失败: " + ex.getMessage());
            }
        } else {
            addSystemTip("错误: 未连接到主机或会话已断开");
        }
    }

    public void appendAiMessageStart() {
        if (isAiResponding)
            return;
        isAiResponding = true;
        currentAiResponse.setLength(0);

        // 创建初始空气泡 "Thinking..."
        currentAiBubble = addTextBubble(false, "Thinking...");

        // 获取内部 TextArea 引用
        // Hacky: access internal structure of BubblePanel -> BubbleFactory
        // BubbleFactory returns BubblePanel(container(BubbleContent))
        try {
            Component container = currentAiBubble.getComponent(0); // box
            if (container instanceof Container) {
                // BubblePanel defines structure: Border(container(content))
                // container adds content at CENTER
                Component internalContainer = ((Container) container).getComponent(0);
                if (internalContainer instanceof Container) {
                    Component content = ((Container) internalContainer).getComponent(0);
                    if (content instanceof JTextArea) {
                        currentAiTextArea = (JTextArea) content;
                        currentAiTextArea.setText(""); // Clear "Thinking..."
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void appendAiChunk(String chunk) {
        if (!isAiResponding)
            appendAiMessageStart();

        currentAiResponse.append(chunk);

        if (currentAiTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                currentAiTextArea.append(chunk);
                currentAiBubble.revalidate();
                currentAiBubble.repaint();
                chatArea.revalidate(); // Essential for MigLayout reflow
                scrollToBottom();
            });
        }
    }

    // 兼容接口
    public void onAiStreamChunk(String chunk) {
        appendAiChunk(chunk);
    }
}
