package com.bluelink.ui;

import com.bluelink.util.AppConfig;
import com.bluelink.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
public class AtMentionManager {

    private final JTextArea inputArea;
    private final JPopupMenu popup;
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private String connectedDeviceName;

    public AtMentionManager(JTextArea inputArea) {
        this.inputArea = inputArea;
        this.popup = new JPopupMenu();
        this.listModel = new DefaultListModel<>();
        this.list = new JList<>(listModel);
        
        initUI();
        initListeners();
    }

    private void initUI() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(UiUtils.FONT_NORMAL);
        list.setFocusable(true);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                if (isSelected) {
                    label.setBackground(UiUtils.COLOR_PRIMARY);
                    label.setForeground(Color.WHITE);
                }
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(150, 100));

        popup.add(scroll);
        popup.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
    }

    private void initListeners() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '@') {
                    showPopup();
                } else if (popup.isVisible() && e.getKeyChar() == ' ') {
                    popup.setVisible(false);
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (popup.isVisible()) {
                    handlePopupNavigation(e);
                }
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && list.getSelectedIndex() != -1) {
                    insertSelection();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ENTER) {
                    insertSelection();
                    e.consume();
                } else if (code == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                    inputArea.requestFocusInWindow();
                    e.consume();
                }
            }
        });
    }

    private void showPopup() {
        String[] models = getModels();
        if ((connectedDeviceName == null || connectedDeviceName.isEmpty()) && models.length == 0) {
            return;
        }
        listModel.clear();
        if (connectedDeviceName != null && !connectedDeviceName.isEmpty()) {
            listModel.addElement(connectedDeviceName);
        }
        for (String model : models) {
            listModel.addElement(model);
        }
        list.setSelectedIndex(0);

        try {
            int pos = inputArea.getCaretPosition();
            Rectangle rect = inputArea.modelToView(pos);
            if (rect != null) {
                popup.show(inputArea, rect.x, rect.y - popup.getPreferredSize().height);
                inputArea.requestFocusInWindow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setConnectedDeviceName(String deviceName) {
        this.connectedDeviceName = deviceName;
        if (popup.isVisible()) {
            showPopup();
        }
    }

    private void handlePopupNavigation(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_DOWN) {
            int index = list.getSelectedIndex();
            if (index < listModel.size() - 1) {
                list.setSelectedIndex(index + 1);
                list.ensureIndexIsVisible(index + 1);
            }
            e.consume();
        } else if (code == KeyEvent.VK_UP) {
            int index = list.getSelectedIndex();
            if (index > 0) {
                list.setSelectedIndex(index - 1);
                list.ensureIndexIsVisible(index - 1);
            }
            e.consume();
        } else if (code == KeyEvent.VK_ENTER) {
            insertSelection();
            e.consume();
        } else if (code == KeyEvent.VK_ESCAPE) {
            popup.setVisible(false);
            e.consume();
        }
    }

    public boolean isPopupVisible() {
        return popup.isVisible();
    }

    public boolean confirmSelection() {
        if (!popup.isVisible()) return false;
        if (listModel.isEmpty()) {
            popup.setVisible(false);
            return false;
        }
        if (list.getSelectedIndex() < 0) {
            list.setSelectedIndex(0);
        }
        insertSelection();
        return true;
    }

    private void insertSelection() {
        String selected = list.getSelectedValue();
        if (selected == null) return;

        try {
            inputArea.replaceSelection(selected + " ");
            popup.setVisible(false);
            inputArea.requestFocusInWindow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static String extractModelMention(String text) {
        String mention = extractRawMention(text);
        if (mention == null) return null;
        if (!isConfiguredModelName(mention)) return null;
        return mention;
    }

    public static String extractRawMention(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("@")) return null;
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex <= 1) return null;
        return trimmed.substring(1, spaceIndex);
    }
    
    public static String stripMention(String text) {
        if (text == null) return null;
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex == -1) return text;
        return text.substring(spaceIndex + 1).trim();
    }

    private static String[] getModels() {
        String model = AppConfig.getAiModel();
        if (model == null) return new String[0];
        String[] parts = model.split(",");
        java.util.List<String> models = new java.util.ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) models.add(v);
        }
        return models.toArray(new String[0]);
    }

    public static boolean isConfiguredModelName(String model) {
        if (model == null || model.isEmpty()) return false;
        String[] models = getModels();
        for (String m : models) {
            if (model.equals(m)) return true;
        }
        return false;
    }
}
