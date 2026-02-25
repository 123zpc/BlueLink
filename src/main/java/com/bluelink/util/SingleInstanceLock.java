package com.bluelink.util;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 单实例锁
 * 通过绑定本地端口确保只有一个应用实例运行
 * 如果发现已运行实例，则唤醒它
 */
public class SingleInstanceLock {

    // 选择一个不太可能被占用的高位端口
    private static final int PORT = 58432; 
    private static ServerSocket serverSocket;
    private static JFrame frame;

    /**
     * 尝试通知已存在的实例
     * @return true 如果成功通知了已存在的实例（说明已有实例在运行）
     *         false 如果连接失败（说明没有实例在运行，或者端口被防火墙阻挡）
     */
    public static boolean notifyExistingInstance() {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("WAKEUP");
            return true;
        } catch (IOException e) {
            // 连接失败，说明端口未被监听，即没有实例在运行
            return false;
        }
    }

    /**
     * 启动监听服务，等待唤醒信号
     */
    public static void startServer() {
        try {
            serverSocket = new ServerSocket(PORT, 10, InetAddress.getByName("127.0.0.1"));
            Thread listenerThread = new Thread(() -> {
                while (serverSocket != null && !serverSocket.isClosed()) {
                    try (Socket client = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                        String line = in.readLine();
                        if ("WAKEUP".equals(line)) {
                            SwingUtilities.invokeLater(() -> {
                                if (frame != null) {
                                    // 恢复窗口状态
                                    int state = frame.getExtendedState();
                                    // 去除最小化状态
                                    state &= ~JFrame.ICONIFIED;
                                    frame.setExtendedState(state);
                                    frame.setVisible(true);
                                    frame.toFront();
                                    frame.requestFocus();
                                    
                                    // 闪烁任务栏 (Windows 特性支持可能有限，但 toFront 通常足够)
                                }
                            });
                        }
                    } catch (IOException e) {
                        // ignore accept errors
                    }
                }
            });
            listenerThread.setDaemon(true);
            listenerThread.setName("SingleInstanceListener");
            listenerThread.start();
        } catch (IOException e) {
            System.err.println("SingleInstanceLock: Failed to bind port " + PORT + ". " + e.getMessage());
            // 如果绑定失败，可能是 notifyExistingInstance 判断失误（例如端口被其他程序占用但不是我们的协议）
            // 这里我们可以选择弹窗提示，或者默默继续（有风险）
            // 为了安全起见，如果不强制退出，就让它运行吧，但无法唤醒
        }
    }

    /**
     * 注册主窗口，用于唤醒时操作
     */
    public static void registerFrame(JFrame appFrame) {
        frame = appFrame;
    }
}
