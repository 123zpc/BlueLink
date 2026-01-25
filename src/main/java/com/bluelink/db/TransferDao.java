package com.bluelink.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 传输记录 DAO
 */
public class TransferDao {

    // 实体类
    public static class LogItem {
        public long id;
        public String type; // TEXT, FILE
        public boolean isSender; // true=SEND, false=RECV
        public String content;
        public long fileSize;
        public long timestamp;
        public String status;

        public LogItem(String type, boolean isSender, String content, long fileSize) {
            this.type = type;
            this.isSender = isSender;
            this.content = content;
            this.fileSize = fileSize;
            this.timestamp = System.currentTimeMillis();
            this.status = "SUCCESS";
        }

        // 构造函数供查询使用
        public LogItem() {
        }
    }

    public static void save(LogItem item) {
        String sql = "INSERT INTO transfer_log (type, direction, content, file_size, timestamp, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.type);
            pstmt.setString(2, item.isSender ? "SEND" : "RECV");
            pstmt.setString(3, item.content);
            pstmt.setLong(4, item.fileSize);
            pstmt.setLong(5, item.timestamp == 0 ? System.currentTimeMillis() : item.timestamp);
            pstmt.setString(6, item.status == null ? "SUCCESS" : item.status);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    item.id = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 分页加载历史记录
     * 
     * @param beforeId 加载该 ID 之前的记录（倒序）。如果为 -1，则加载最新的记录。
     * @param limit    加载条数
     * @return 记录列表（按时间正序排列）
     */
    public static List<LogItem> loadHistory(long beforeId, int limit) {
        List<LogItem> list = new ArrayList<>();
        String sql;
        if (beforeId == -1 || beforeId == Long.MAX_VALUE) {
            sql = "SELECT * FROM transfer_log ORDER BY id DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM transfer_log WHERE id < ? ORDER BY id DESC LIMIT ?";
        }

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (beforeId == -1 || beforeId == Long.MAX_VALUE) {
                pstmt.setInt(1, limit);
            } else {
                pstmt.setLong(1, beforeId);
                pstmt.setInt(2, limit);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LogItem item = new LogItem();
                    item.id = rs.getLong("id");
                    item.type = rs.getString("type");
                    String dir = rs.getString("direction");
                    item.isSender = "SEND".equals(dir);
                    item.content = rs.getString("content");
                    item.fileSize = rs.getLong("file_size");
                    item.timestamp = rs.getLong("timestamp");
                    item.status = rs.getString("status");
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 数据库查询出来是倒序的 (最新的在前)，UI 需要正序 (旧的在前，新的在后)
        java.util.Collections.reverse(list);
        return list;
    }

    // loadAll 方法已废弃，移除或保留均可，目前我们替换它
    public static List<LogItem> loadAll() {
        return loadHistory(-1, 1000); // 兼容旧代码，但仅限1000条
    }

    /**
     * 更新消息状态
     */
    public static void updateStatus(long id, String status) {
        String sql = "UPDATE transfer_log SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 清空所有聊天记录
     */
    public static void clearAll() {
        String sql = "DELETE FROM transfer_log";
        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
