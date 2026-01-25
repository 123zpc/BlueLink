package com.bluelink.net;

import com.bluelink.net.jna.WinsockNative;
import com.bluelink.net.jna.WinsockNative.SOCKADDR_BTH;
import com.bluelink.net.protocol.ProtocolWriter;
import com.bluelink.net.jna.JnaSocketOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 蓝牙客户端
 */
public class BluetoothClient {
    private int clientSocket = WinsockNative.INVALID_SOCKET;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TransferListener listener;
    private JnaSocketOutputStream outputStream;

    public void setListener(TransferListener listener) {
        this.listener = listener;
    }

    public void connect(String addressStr) {
        executor.submit(() -> {
            WinsockNative lib = WinsockNative.INSTANCE;

            clientSocket = lib.socket(WinsockNative.AF_BTH, WinsockNative.SOCK_STREAM, WinsockNative.BTHPROTO_RFCOMM);
            if (clientSocket == WinsockNative.INVALID_SOCKET) {
                notifyError("创建客户端 Socket 失败");
                return;
            }

            SOCKADDR_BTH addr = new SOCKADDR_BTH();
            try {
                addr.btAddr = Long.parseLong(addressStr);
            } catch (NumberFormatException e) {
                notifyError("无效的地址格式");
                close();
                return;
            }
            addr.port = 0;

            // 关键：指定服务 UUID (SPP)，让系统自动通过 SDP 查找对应端口
            addr.serviceClassId = new WinsockNative.GUID();
            // SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
            // Data1: 0x00001101
            addr.serviceClassId.Data1 = 0x00001101;
            addr.serviceClassId.Data2 = (short) 0x0000;
            addr.serviceClassId.Data3 = (short) 0x1000;
            // Data4: 80 00 00 80 5F 9B 34 FB
            addr.serviceClassId.Data4 = new byte[] {
                    (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80,
                    (byte) 0x5F, (byte) 0x9B, (byte) 0x34, (byte) 0xFB
            };

            addr.write();

            if (lib.connect(clientSocket, addr, addr.size()) == WinsockNative.SOCKET_ERROR) {
                notifyError("连接失败: " + lib.WSAGetLastError());
                close();
                return;
            }

            outputStream = new JnaSocketOutputStream(clientSocket);
            notifyConnection(true, "已连接到: " + addressStr);
        });
    }

    public void send(String message) throws IOException {
        if (clientSocket == WinsockNative.INVALID_SOCKET || outputStream == null) {
            throw new IOException("未连接");
        }

        // 文本消息约定 name = "MSG"
        byte[] packet = ProtocolWriter.createPacket("MSG", message.getBytes("UTF-8"));
        outputStream.write(packet);
        outputStream.flush();
    }

    public void sendFile(File file) throws IOException {
        if (clientSocket == WinsockNative.INVALID_SOCKET || outputStream == null) {
            throw new IOException("未连接");
        }

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

        // 这里可以通过 listener 更新进度 (mock)
        if (listener != null)
            listener.onTransferProgress(file.getName(), file.length(), file.length());
    }

    private void close() {
        if (clientSocket != WinsockNative.INVALID_SOCKET) {
            WinsockNative.INSTANCE.closesocket(clientSocket);
            clientSocket = WinsockNative.INVALID_SOCKET;
        }
    }

    private void notifyError(String msg) {
        if (listener != null)
            listener.onError(msg);
    }

    private void notifyConnection(boolean connected, String msg) {
        if (listener != null)
            listener.onConnectionStatusChanged(connected, msg);
    }
}
