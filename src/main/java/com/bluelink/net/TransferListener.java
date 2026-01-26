package com.bluelink.net;

import java.io.File;

/**
 * 传输事件监听器
 */
public interface TransferListener {
    /**
     * 收到文本消息
     * @param sender 发送者
     * @param content 内容
     */
    void onMessageReceived(String sender, String content);

    /**
     * 收到文件
     * @param sender 发送者
     * @param file 文件对象
     * @param originalName 原始文件名 (用于关联进度)
     */
    void onFileReceived(String sender, File file, String originalName);

    /**
     * 传输进度更新
     * @param fileName 文件名
     * @param current 当前字节数
     * @param total 总字节数
     * @param isReceive true=接收进度, false=发送进度
     */
    void onTransferProgress(String fileName, long current, long total, boolean isReceive);

    /**
     * 连接状态变更
     * @param isConnected 是否已连接
     * @param deviceName 设备名称
     */
    void onConnectionStatusChanged(boolean isConnected, String deviceName);
    
    /**
     * 发生错误
     * @param message 错误信息
     */
    void onError(String message);
    
    /**
     * 当会话建立时调用
     */
    default void onSessionCreated(BluetoothSession session) {}
}
