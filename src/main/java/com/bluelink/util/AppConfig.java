package com.bluelink.util;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

/**
 * 应用配置管理
 * 负责管理应用配置和数据存储路径
 */
public class AppConfig {
    private static final Properties props = new Properties();
    private static final String APP_NAME = "BlueLink";
    public static final String APP_DATA_DIR;
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static File configFile;

    static {
        // 1. 确定应用数据目录: %APPDATA%/BlueLink
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        File dir = new File(appData, APP_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        APP_DATA_DIR = dir.getAbsolutePath();
        System.out.println("数据目录: " + APP_DATA_DIR);

        loadConfig();
    }

    private static void loadConfig() {
        // 2. 优先加载 AppData 下的配置文件
        configFile = new File(APP_DATA_DIR, CONFIG_FILE_NAME);
        if (configFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                props.load(fis);
                System.out.println("加载配置文件: " + configFile.getAbsolutePath());
            } catch (Exception e) {
                System.out.println("加载配置文件失败: " + e.getMessage());
            }
        } else {
            // 3. 如果本地没有，尝试读取内置默认配置（只读），但 configFile 依然指向 AppData
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is != null) {
                    props.load(is); // 加载默认值
                }
            } catch (Exception e) {
                // ignore
            }
            System.out.println("使用默认配置，新配置将保存至: " + configFile.getAbsolutePath());
        }
    }

    public static void saveConfig(String key, String value) {
        props.setProperty(key, value);
        saveToFile();
    }

    private static void saveToFile() {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(configFile)) {
            props.store(fos, "BlueLink User Settings");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否为开发模式
     */
    public static boolean isDevMode() {
        return Boolean.parseBoolean(props.getProperty("dev.mode", "false"));
    }

    public static final String DEV_CODE = "000000";

    public static boolean isDevBypassCode(String code) {
        return isDevMode() && DEV_CODE.equals(code);
    }

    /**
     * 获取连接超时时间（毫秒）
     */
    public static int getConnectionTimeoutMs() {
        try {
            int seconds = Integer.parseInt(props.getProperty("connection.timeout", "30"));
            return seconds * 1000;
        } catch (NumberFormatException e) {
            return 30 * 1000;
        }
    }

    public static int getConnectionTimeoutSeconds() {
        return getConnectionTimeoutMs() / 1000;
    }

    public static void setConnectionTimeout(int seconds) {
        saveConfig("connection.timeout", String.valueOf(seconds));
    }

    /**
     * 获取文件下载路径
     * 默认为 用户主目录/Downloads
     */
    public static String getDownloadPath() {
        String userHome = System.getProperty("user.home");
        String defaultPath = new File(userHome, "Downloads").getAbsolutePath();
        // 如果系统下载目录不存在（极少数情况），回退到 AppData
        if (!new File(defaultPath).exists()) {
            defaultPath = new File(APP_DATA_DIR, "downloads").getAbsolutePath();
        }
        return props.getProperty("download.path", defaultPath);
    }

    public static void setDownloadPath(String path) {
        saveConfig("download.path", path);
    }
}
