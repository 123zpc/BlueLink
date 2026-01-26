package com.bluelink.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库管理器
 * 负责 H2 数据库的初始化和连接管理
 */
public class DatabaseManager {

    // 使用 AppData 目录存储数据库
    private static final String DB_DIR = com.bluelink.util.AppConfig.APP_DATA_DIR + "/data";
    private static final String DB_NAME = "bluelink";
    // jdbc:h2:file:C:/Users/.../AppData/Roaming/BlueLink/data/bluelink;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
    private static final String DB_URL = "jdbc:h2:file:" + DB_DIR.replace("\\", "/") + "/" + DB_NAME + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    static {
        // 确保数据库目录存在
        java.io.File dir = new java.io.File(DB_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                System.err.println("无法创建数据库目录: " + DB_DIR);
            }
        }

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void initDatabase() {
        System.out.println("正在初始化数据库: " + DB_URL);
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // 创建传输记录表
            String sql = "CREATE TABLE IF NOT EXISTS transfer_log (" +
                    "id IDENTITY PRIMARY KEY, " +
                    "type VARCHAR(20), " + // TEXT, FILE
                    "direction VARCHAR(10), " + // SEND, RECV
                    "content VARCHAR(MAX), " + // 文本内容或文件路径
                    "file_size BIGINT, " +
                    "timestamp BIGINT, " +
                    "status VARCHAR(20))"; // SUCCESS, FAILED

            stmt.execute(sql);
            System.out.println("数据库初始化完成.");

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }
}
