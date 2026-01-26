package com.bluelink;

import com.bluelink.ui.ModernQQFrame;
import com.bluelink.util.UiUtils;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // 确保数据库初始化
        com.bluelink.db.DatabaseManager.initDatabase();

        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            ModernQQFrame frame = new ModernQQFrame();
            frame.loadHistory(); // 加载历史
            frame.setVisible(true);
        });
    }
}
