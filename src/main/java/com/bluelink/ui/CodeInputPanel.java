package com.bluelink.ui;

import com.bluelink.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 验证码风格的 6 位连接码输入组件
 * 每个数字一个独立格子，类似短信验证码输入
 */
public class CodeInputPanel extends JPanel {

    private final JTextField[] fields = new JTextField[6];
    private Runnable onComplete;

    public CodeInputPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
        setOpaque(false);

        for (int i = 0; i < 6; i++) {
            fields[i] = createSingleField(i);
            add(fields[i]);
        }
    }

    private JTextField createSingleField(int index) {
        JTextField field = new JTextField() { // 不指定列数
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 只绘制底部下划线
                int y = getHeight() - 5;
                if (isFocusOwner()) {
                    g2.setColor(UiUtils.COLOR_PRIMARY);
                    g2.setStroke(new BasicStroke(3));
                } else if (getText().isEmpty()) {
                    g2.setColor(new Color(180, 180, 180));
                    g2.setStroke(new BasicStroke(2));
                } else {
                    g2.setColor(new Color(100, 100, 100));
                    g2.setStroke(new BasicStroke(2));
                }
                g2.drawLine(4, y, getWidth() - 4, y);
                g2.dispose();
            }
        };

        field.setPreferredSize(new Dimension(60, 60));
        field.setMinimumSize(new Dimension(60, 60));
        field.setMaximumSize(new Dimension(60, 60));
        field.setFont(new Font("Consolas", Font.BOLD, 24));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(8, 10, 12, 10));
        field.setMargin(new Insets(0, 6, 0, 6)); // 显式设置内部边距
        field.putClientProperty("JTextField.placeholderText", ""); // 禁用 FlatLaf placeholder
        field.setForeground(new Color(50, 50, 50));
        field.setCaretColor(UiUtils.COLOR_PRIMARY);

        // 限制只能输入数字，支持粘贴多位
        field.setDocument(new javax.swing.text.PlainDocument() {
            @Override
            public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null)
                    return;
                String filtered = str.replaceAll("[^0-9]", "");
                if (filtered.isEmpty())
                    return;

                // 如果粘贴的是多位数字，分配到各个输入框
                if (filtered.length() > 1) {
                    SwingUtilities.invokeLater(() -> {
                        pasteCode(filtered, index);
                    });
                    return;
                }

                // 单个字符输入
                if (getLength() == 0) {
                    super.insertString(0, filtered.substring(0, 1), a);
                    // 自动跳到下一个
                    SwingUtilities.invokeLater(() -> {
                        if (index < 5) {
                            fields[index + 1].requestFocus();
                        } else {
                            // 输入完成
                            if (onComplete != null && isComplete()) {
                                // 简单的防抖动，防止最后一位输入触发多次
                                if (!fields[5].hasFocus()) return; // 只有焦点在最后一个时才触发（其实也不完全靠谱，交给上层处理更好）
                                // 让焦点移除，避免重复输入
                                fields[5].transferFocus();
                                onComplete.run();
                            }
                        }
                    });
                }
            }
        });

        // 退格键处理：删除后跳到上一个
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (field.getText().isEmpty() && index > 0) {
                        fields[index - 1].setText("");
                        fields[index - 1].requestFocus();
                    }
                }
                // 左右箭头导航
                if (e.getKeyCode() == KeyEvent.VK_LEFT && index > 0) {
                    fields[index - 1].requestFocus();
                }
                if (e.getKeyCode() == KeyEvent.VK_RIGHT && index < 5) {
                    fields[index + 1].requestFocus();
                }
            }
        });

        // 聚焦时重绘下划线
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.repaint();
            }
        });

        return field;
    }

    /**
     * 获取输入的完整连接码
     */
    public String getCode() {
        StringBuilder sb = new StringBuilder();
        for (JTextField f : fields) {
            sb.append(f.getText());
        }
        return sb.toString();
    }

    /**
     * 是否已输入完整 6 位
     */
    public boolean isComplete() {
        return getCode().length() == 6;
    }

    /**
     * 清空输入
     */
    public void clear() {
        for (JTextField f : fields) {
            f.setText("");
        }
        fields[0].requestFocus();
    }

    /**
     * 设置完成回调
     */
    public void setOnComplete(Runnable callback) {
        this.onComplete = callback;
    }

    /**
     * 设置启用/禁用
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (JTextField f : fields) {
            f.setEnabled(enabled);
            f.setBackground(enabled ? new Color(250, 250, 250) : new Color(240, 240, 240));
        }
    }

    /**
     * 请求第一个输入框的焦点
     */
    public void focusFirst() {
        fields[0].requestFocus();
    }

    /**
     * 粘贴连接码，从指定位置开始分配到各输入框
     */
    private void pasteCode(String code, int startIndex) {
        for (int i = 0; i < code.length() && (startIndex + i) < 6; i++) {
            fields[startIndex + i].setText(String.valueOf(code.charAt(i)));
        }
        // 聚焦到最后一个填充的位置或下一个空位
        int focusIndex = Math.min(startIndex + code.length(), 5);
        fields[focusIndex].requestFocus();

        // 如果输入完成，触发回调
        if (isComplete() && onComplete != null) {
            onComplete.run();
        }
    }
}
