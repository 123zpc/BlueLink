package com.bluelink.ui;

import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * 基础聊天面板 (抽象类)
 * 封装了统一的 UI 布局、气泡渲染、滚动逻辑
 * 保证 MainChatPanel 和 AiChatPanel 的视觉一致性
 */
public abstract class BaseChatPanel extends JPanel {

    protected JPanel headerPanel;
    protected JLabel headerLabel;

    protected JPanel chatArea;
    protected JScrollPane chatScrollPane;

    protected JPanel inputPanel;
    protected JTextArea inputArea;
    protected JButton sendButton;

    public BaseChatPanel() {
        initBaseUI();
    }

    private void initBaseUI() {
        // Layout: Header (Top), Chat (Center), Input (Bottom)
        // 保持与 ModernQQFrame createContentPanel 一致的约束
        setLayout(new MigLayout("insets 0, fill, wrap 1", "[grow, fill]", "[40px!, fill][grow, fill][150px!, fill]"));
        setBackground(Color.WHITE);

        // 1. Header
        headerPanel = new JPanel(new MigLayout("insets 10 30 10 30, fill"));
        headerPanel.setBackground(new Color(245, 245, 245));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)));

        headerLabel = new JLabel("Chat Title");
        headerLabel.setFont(UiUtils.FONT_BOLD);
        headerPanel.add(headerLabel);

        add(headerPanel, "cell 0 0");

        // 2. Chat Area
        chatArea = new JPanel(new MigLayout("insets 10, fillx, wrap 1", "[grow, fill]", "[]"));
        chatArea.setBackground(Color.WHITE);

        chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(chatScrollPane, "cell 0 1");

        // 3. Input Area
        inputPanel = new JPanel(new MigLayout("insets 10, fill", "[grow][]", "[grow][]"));
        inputPanel.setBackground(Color.WHITE);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));

        inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(UiUtils.FONT_NORMAL.deriveFont(14f));
        inputArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Key bindings
        initInputKeyBindings();

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);

        inputPanel.add(inputScroll, "cell 0 0, span 2, grow"); // Row 0

        // Send Button
        sendButton = new JButton("发送");
        sendButton.setBackground(UiUtils.COLOR_PRIMARY);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(UiUtils.FONT_NORMAL);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.addActionListener(e -> performSendAction());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.add(sendButton);

        inputPanel.add(btnPanel, "cell 1 1"); // Row 1 (Bottom Right)

        add(inputPanel, "cell 0 2");
    }

    private void initInputKeyBindings() {
        inputArea.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "send");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performSendAction();
            }
        });

        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                "newline");
        inputArea.getActionMap().put("newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputArea.append("\n");
            }
        });
    }

    protected void performSendAction() {
        String text = inputArea.getText().trim();
        if (text.isEmpty())
            return;
        onSend(text);
    }

    // 子类实现发送逻辑
    protected abstract void onSend(String text);

    // --- Helper Methods ---

    public void setHeaderTitle(String title) {
        headerLabel.setText(title);
    }

    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    public void clearInput() {
        inputArea.setText("");
    }

    public void clearChat() {
        chatArea.removeAll();
        chatArea.revalidate();
        chatArea.repaint();
    }

    // --- Bubble Rendering ---

    public BubblePanel addTextBubble(boolean isSender, String text) {
        // Wrapper for alignment
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        BubblePanel bubble = BubbleFactory.createTextBubble(isSender, text);

        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap");
        scrollToBottom();

        return bubble;
    }

    public BubblePanel addFileBubble(boolean isSender, File file) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        BubblePanel bubble = BubbleFactory.createFileBubble(isSender, file);

        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap");
        scrollToBottom();

        return bubble;
    }

    // --- Insert for History ---

    public BubblePanel insertTextBubbleAt(int index, boolean isSender, String text) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        BubblePanel bubble = BubbleFactory.createTextBubble(isSender, text);

        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap", index);
        // Do not scroll to bottom when loading history

        return bubble;
    }

    public BubblePanel insertFileBubbleAt(int index, boolean isSender, File file) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        BubblePanel bubble = BubbleFactory.createFileBubble(isSender, file);

        String constraints = isSender ? "al right, width ::80%" : "al left, width ::80%";
        wrapper.add(bubble, constraints);

        chatArea.add(wrapper, "growx, wrap", index);

        return bubble;
    }

    public void addSystemTip(String text) {
        JLabel tip = new JLabel(text);
        tip.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
        tip.setForeground(Color.GRAY);
        tip.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel wrapper = new JPanel(new MigLayout("insets 5, fillx, alignx center", "[center]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(tip);

        chatArea.add(wrapper, "growx, wrap");
        scrollToBottom();
    }
}
