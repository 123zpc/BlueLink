package com.bluelink.util;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.Method;

/**
 * 文件图标工具类
 * 用于获取清晰的文件大图标 (32x32)
 */
public class FileIconUtils {

    public static Icon getFileIcon(File file) {
        if (file == null || !file.exists()) {
            return UIManager.getIcon("FileView.fileIcon");
        }
        
        try {
            // 尝试使用 sun.awt.shell.ShellFolder 获取大图标 (32x32)
            // 这种方式在 Windows 上能获取到比 FileSystemView 更清晰的图标
            Class<?> shellFolderClass = Class.forName("sun.awt.shell.ShellFolder");
            Method getShellFolder = shellFolderClass.getMethod("getShellFolder", File.class);
            Object sf = getShellFolder.invoke(null, file);
            Method getIcon = shellFolderClass.getMethod("getIcon", boolean.class);
            // true = Large Icon (通常为 32x32)
            return (Icon) getIcon.invoke(sf, true);
        } catch (Exception e) {
            // 忽略异常 (如模块访问限制)，回退到标准 API
        }
        
        return javax.swing.filechooser.FileSystemView.getFileSystemView().getSystemIcon(file);
    }
}
