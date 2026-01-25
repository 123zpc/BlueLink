package com.bluelink.util;

import com.bluelink.ui.CodePreviewDialog;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件预览工具类
 * 负责分发文件打开请求：代码文件走内置预览，其他走系统默认
 */
public class FilePreviewUtils {

    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "sql", "txt", "xml", "json",
            "css", "html", "js", "ts",
            "py", "c", "cpp", "h", "cs",
            "sh", "bat", "properties", "ini",
            "md", "yml", "yaml", "log", "gradle"));

    public static void openFile(File file) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(null, "文件不存在: " + file.getAbsolutePath(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String fileName = file.getName().toLowerCase();
        String ext = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            ext = fileName.substring(i + 1);
        }

        if (CODE_EXTENSIONS.contains(ext)) {
            // 内置预览
            CodePreviewDialog.preview(file);
        } else {
            // 系统默认打开
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "无法打开文件: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
