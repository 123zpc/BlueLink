package com.bluelink;

import com.bluelink.ui.ModernQQFrame;
import com.bluelink.util.UiUtils;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UiUtils.initTheme();
            new ModernQQFrame().setVisible(true);
        });
    }
}
