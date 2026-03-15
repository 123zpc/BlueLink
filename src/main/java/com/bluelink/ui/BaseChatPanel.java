package com.bluelink.ui;

import com.bluelink.ui.bubble.BubbleFactory;
import com.bluelink.ui.bubble.BubblePanel;
import com.bluelink.util.AppConfig;
import com.bluelink.util.UiUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
    protected JLayeredPane chatLayer;
    protected JButton backToBottomButton;

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

        backToBottomButton = new JButton("返回底部") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                super.paintComponent(g);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(200, 200, 200, 100)); // 浅浅的半透明边框，模拟微弱深度
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        backToBottomButton.setFont(UiUtils.FONT_NORMAL.deriveFont(12f));
        backToBottomButton.setBackground(new Color(250, 252, 255, 230)); // 略带主色调的白
        backToBottomButton.setForeground(UiUtils.COLOR_PRIMARY);
        backToBottomButton.setContentAreaFilled(false);
        backToBottomButton.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        backToBottomButton.setFocusPainted(false);
        backToBottomButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backToBottomButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                backToBottomButton.setBackground(new Color(235, 245, 255, 240));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                backToBottomButton.setBackground(new Color(250, 252, 255, 230));
            }
        });
        backToBottomButton.setVisible(false);
        backToBottomButton.addActionListener(e -> scrollToBottom());

        chatLayer = new JLayeredPane();
        chatLayer.setLayout(null);
        chatLayer.add(chatScrollPane, JLayeredPane.DEFAULT_LAYER);
        chatLayer.add(backToBottomButton, JLayeredPane.PALETTE_LAYER);
        chatLayer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutChatLayer();
            }
        });

        chatScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> updateBackToBottomVisibility());

        add(chatLayer, "cell 0 1");
        updateBackToBottomVisibility();

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
        // Right-click menu and keys
        initInputRightClickMenu();
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

    private void layoutChatLayer() {
        int w = chatLayer.getWidth();
        int h = chatLayer.getHeight();
        chatScrollPane.setBounds(0, 0, w, h);

        Dimension btnSize = backToBottomButton.getPreferredSize();
        int x = Math.max(0, w - btnSize.width - 16);
        int y = Math.max(0, h - btnSize.height - 16);
        backToBottomButton.setBounds(x, y, btnSize.width, btnSize.height);
    }

    private void updateBackToBottomVisibility() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            int bottom = vertical.getMaximum() - vertical.getVisibleAmount();
            boolean atBottom = vertical.getValue() >= bottom - 5;
            backToBottomButton.setVisible(!atBottom);
        });
    }

    private void initInputRightClickMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem pasteItem = new JMenuItem("粘贴 (Ctrl+V)");
        pasteItem.addActionListener(e -> {
            Action pasteAction = inputArea.getActionMap().get("paste-check");
            if (pasteAction != null) {
                pasteAction.actionPerformed(new ActionEvent(inputArea, ActionEvent.ACTION_PERFORMED, null));
            } else {
                inputArea.paste();
            }
        });

        JMenuItem copyItem = new JMenuItem("复制 (Ctrl+C)");
        copyItem.addActionListener(e -> inputArea.copy());

        JMenuItem cutItem = new JMenuItem("剪切 (Ctrl+X)");
        cutItem.addActionListener(e -> inputArea.cut());

        JMenu sendMethodMenu = new JMenu("发送方式");
        JRadioButtonMenuItem enterToSendMessage = new JRadioButtonMenuItem("按 Enter 键发送");
        JRadioButtonMenuItem ctrlEnterToSendMessage = new JRadioButtonMenuItem("按 Ctrl+Enter 键发送");
        
        ButtonGroup bg = new ButtonGroup();
        bg.add(enterToSendMessage);
        bg.add(ctrlEnterToSendMessage);

        enterToSendMessage.addActionListener(e -> AppConfig.setEnterToSend(true));
        ctrlEnterToSendMessage.addActionListener(e -> AppConfig.setEnterToSend(false));

        sendMethodMenu.add(enterToSendMessage);
        sendMethodMenu.add(ctrlEnterToSendMessage);

        popupMenu.add(copyItem);
        popupMenu.add(cutItem);
        popupMenu.add(pasteItem);
        popupMenu.addSeparator();
        popupMenu.add(sendMethodMenu);

        inputArea.setComponentPopupMenu(popupMenu);

        // Update selected state dynamically on popup opening
        popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                boolean isEnterToSend = AppConfig.isEnterToSend();
                enterToSendMessage.setSelected(isEnterToSend);
                ctrlEnterToSendMessage.setSelected(!isEnterToSend);
                
                boolean hasSelection = inputArea.getSelectedText() != null;
                copyItem.setEnabled(hasSelection);
                cutItem.setEnabled(hasSelection);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
    }

    private void initInputKeyBindings() {
        // Remove default enter behavior and make it dynamic
        InputMap inputMap = inputArea.getInputMap();
        ActionMap actionMap = inputArea.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "dynamic-enter");
        actionMap.put("dynamic-enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (AppConfig.isEnterToSend()) {
                    performSendAction();
                } else {
                    inputArea.append("\n");
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "dynamic-ctrl-enter");
        actionMap.put("dynamic-ctrl-enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!AppConfig.isEnterToSend()) {
                    performSendAction();
                } else {
                    inputArea.append("\n");
                }
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
        chatArea.revalidate();
        chatArea.repaint();
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

    public BubblePanel insertMarkdownBubbleAt(int index, boolean isSender, String htmlContent) {
        JPanel wrapper = new JPanel(new MigLayout("insets 2, fillx, gap 0", "[grow]", "[]"));
        wrapper.setOpaque(false);

        BubblePanel bubble = BubbleFactory.createMarkdownBubble(isSender, htmlContent);

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

    public void insertSystemTipAt(int index, String text) {
        JLabel tip = new JLabel(text);
        tip.setFont(UiUtils.FONT_NORMAL.deriveFont(10f));
        tip.setForeground(Color.GRAY);
        tip.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel wrapper = new JPanel(new MigLayout("insets 5, fillx, alignx center", "[center]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(tip);

        chatArea.add(wrapper, "growx, wrap", index);
    }
}
