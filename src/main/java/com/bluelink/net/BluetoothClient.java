package com.bluelink.net;

import com.bluelink.net.jna.WinsockNative;
import com.bluelink.net.jna.WinsockNative.SOCKADDR_BTH;
import com.bluelink.net.protocol.ProtocolWriter;
import com.bluelink.net.jna.JnaSocketOutputStream;
import com.sun.jna.Native;
import com.bluelink.util.BluetoothUtils;

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
    private final ExecutorService executor = Executors.newCachedThreadPool();
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

            System.out.println("[Client] 正在连接: " + addressStr + " (UUID: SPP)");
            
            // JNA: 在调用前清除错误，确保获取的是本次调用的错误
            Native.setLastError(0);
            int connectResult = lib.connect(clientSocket, addr, addr.size());
            System.out.println("[Client] connect 返回值: " + connectResult);
            
            if (connectResult == WinsockNative.SOCKET_ERROR) {
                // JNA 推荐使用 Native.getLastError() 来获取系统调用的错误码
                // 因为 JNA 内部机制可能会重置 GetLastError
                int errorCode = Native.getLastError();
                
                // 如果 Native.getLastError() 也没获取到（还是0），再尝试 WSAGetLastError 作为保底
                if (errorCode == 0) {
                     errorCode = lib.WSAGetLastError();
                }
                
                System.out.println("[Client] 连接失败，错误码: " + errorCode);
                notifyError("连接失败: " + errorCode);
                close();
                return;
            }

            System.out.println("[Client] Socket 连接成功，正在创建会话...");
            outputStream = new JnaSocketOutputStream(clientSocket);
            
            BluetoothSession session = new BluetoothSession(clientSocket, listener);
            if (listener != null) {
                listener.onSessionCreated(session);
            }
            session.start();

            String code = BluetoothUtils.addressToCode(addr.btAddr);
            notifyConnection(true, code);
        });
    }

    // 废弃的方法，现在通过 Session 发送
    @Deprecated
    public void send(String message) throws IOException {
        // 兼容旧代码，但建议使用 Session
        if (clientSocket == WinsockNative.INVALID_SOCKET || outputStream == null) {
            throw new IOException("未连接");
        }
        System.out.println("[Client] 准备发送消息: " + message);
        byte[] packet = ProtocolWriter.createPacket("MSG", message.getBytes("UTF-8"));
        outputStream.write(packet);
        outputStream.flush();
    }

    @Deprecated
    public void sendFile(File file) throws IOException {
        if (clientSocket == WinsockNative.INVALID_SOCKET || outputStream == null) {
            throw new IOException("未连接");
        }
        byte[] fileData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(fileData);
        }
        byte[] packet = ProtocolWriter.createPacket(file.getName(), fileData);
        outputStream.write(packet);
        outputStream.flush();
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
