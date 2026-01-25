package com.bluelink.ui;

import com.bluelink.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 系统托盘管理器
 * 使用 JPopupMenu 替代 AWT PopupMenu 以正确显示中文
 */
public class TrayManager {

    private final JFrame frame;
    private TrayIcon trayIcon;
    private JPopupMenu popupMenu;

    public TrayManager(JFrame frame) {
        this.frame = frame;
        initTray();
    }

    private void initTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image image = createTrayImage();

        // 使用 Swing JPopupMenu (正确显示中文)
        popupMenu = new JPopupMenu();
        popupMenu.setFont(UiUtils.FONT_NORMAL);

        JMenuItem showItem = new JMenuItem("显示窗口");
        showItem.setFont(UiUtils.FONT_NORMAL);
        showItem.addActionListener(e -> {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
        });
        popupMenu.add(showItem);

        popupMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setFont(UiUtils.FONT_NORMAL);
        exitItem.addActionListener(e -> {
            // 自定义按钮文本
            UIManager.put("OptionPane.yesButtonText", "确定退出");
            UIManager.put("OptionPane.noButtonText", "取消");

            // 创建带图标的确认对话框
            ImageIcon icon = new ImageIcon(UiUtils.createAppIcon().getScaledInstance(48, 48, Image.SCALE_SMOOTH));

            int result = JOptionPane.showConfirmDialog(
                    frame,
                    "确定要退出 BlueLink 吗？\n\n关闭后将无法接收消息。",
                    "退出确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    icon);

            // 恢复默认按钮文本
            UIManager.put("OptionPane.yesButtonText", "Yes");
            UIManager.put("OptionPane.noButtonText", "No");

            if (result == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });
        popupMenu.add(exitItem);

        // TrayIcon 不使用 AWT PopupMenu，而是通过鼠标事件弹出 JPopupMenu
        trayIcon = new TrayIcon(image, "BlueLink");
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // 左键点击：显示窗口
                    frame.setVisible(true);
                    frame.setExtendedState(JFrame.NORMAL);
                    frame.toFront();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    // 使用 MouseInfo 获取真正的鼠标屏幕坐标
                    Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

                    // 计算菜单尺寸
                    Dimension menuSize = popupMenu.getPreferredSize();
                    int x = mouseLocation.x;
                    int y = mouseLocation.y - menuSize.height; // 在鼠标位置上方显示

                    // 确保不超出屏幕边界
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    if (x + menuSize.width > screenSize.width) {
                        x = screenSize.width - menuSize.width;
                    }
                    if (y < 0) {
                        y = mouseLocation.y; // 如果上方空间不够，则在下方显示
                    }

                    popupMenu.setLocation(x, y);
                    popupMenu.setInvoker(popupMenu);
                    popupMenu.setVisible(true);
                }
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private Image createTrayImage() {
        return UiUtils.createAppIcon();
    }
}
