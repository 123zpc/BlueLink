package com.bluelink.net;

import com.bluelink.net.jna.JnaSocketInputStream;
import com.bluelink.net.jna.JnaSocketOutputStream;
import com.bluelink.net.jna.WinsockNative;
import com.bluelink.net.protocol.ProtocolReader;
import com.bluelink.net.protocol.ProtocolWriter;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 蓝牙会话
 * 封装底层的 Socket，处理双向通信（读/写）
 */
public class BluetoothSession {
    private final int socket;
    private final JnaSocketInputStream inputStream;
    private final JnaSocketOutputStream outputStream;
    private final DataInputStream dataInputStream;
    private volatile boolean running = true;
    private TransferListener listener;
    private Thread readThread;

    public BluetoothSession(int socket, TransferListener listener) {
        this.socket = socket;
        this.listener = listener;
        this.inputStream = new JnaSocketInputStream(socket);
        this.outputStream = new JnaSocketOutputStream(socket);
        this.dataInputStream = new DataInputStream(inputStream);
    }

    public void start() {
        readThread = new Thread(this::readLoop, "Session-Reader");
        readThread.start();
    }

    private void readLoop() {
        System.out.println("[Session] 开始读取循环");
        while (running) {
            try {
                ProtocolReader.Packet packet = ProtocolReader.readPacket(dataInputStream);
                if (packet == null) {
                    System.out.println("[Session] 读取到 EOF，连接断开");
                    close();
                    break;
                }

                if ("MSG".equals(packet.name)) {
                    String text = new String(packet.data, "UTF-8");
                    if (listener != null) {
                        listener.onMessageReceived("Remote", text);
                    }
                } else {
                    // 保存文件到配置的下载目录
                    String downloadDir = com.bluelink.util.AppConfig.getDownloadPath();
                    File dir = new File(downloadDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    
                    // 处理重名文件：filename.txt -> filename(1).txt
                    File file = new File(dir, packet.name);
                    String fileName = packet.name;
                    String baseName = fileName;
                    String ext = "";
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        baseName = fileName.substring(0, dotIndex);
                        ext = fileName.substring(dotIndex);
                    }
                    
                    int counter = 1;
                    while (file.exists()) {
                        file = new File(dir, baseName + "(" + counter + ")" + ext);
                        counter++;
                    }

                    java.nio.file.Files.write(file.toPath(), packet.data);
                    if (listener != null) {
                        listener.onFileReceived("Remote", file);
                    }
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Session] 读取错误: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("连接断开: " + e.getMessage());
                    }
                    close();
                }
                break;
            }
        }
    }

    public void sendMessage(String message) throws IOException {
        if (!running) throw new IOException("会话已关闭");
        System.out.println("[Session] 发送消息: " + message);
        byte[] packet = ProtocolWriter.createPacket("MSG", message.getBytes("UTF-8"));
        outputStream.write(packet);
        outputStream.flush();
    }

    public void sendFile(File file) throws IOException {
        if (!running) throw new IOException("会话已关闭");
        if (file.length() > 50 * 1024 * 1024) {
            throw new IOException("文件过大(限制 50MB)");
        }

        byte[] fileData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(fileData);
        }

        byte[] packet = ProtocolWriter.createPacket(file.getName(), fileData);
        outputStream.write(packet);
        outputStream.flush();
        
        if (listener != null)
            listener.onTransferProgress(file.getName(), file.length(), file.length());
    }

    public void close() {
        running = false;
        try {
            // 关闭 Socket 会导致 read 抛出异常从而退出循环
            WinsockNative.INSTANCE.closesocket(socket);
        } catch (Exception e) {
            // ignore
        }
        if (listener != null) {
            listener.onConnectionStatusChanged(false, null);
        }
    }
    
    public boolean isClosed() {
        return !running;
    }
}
