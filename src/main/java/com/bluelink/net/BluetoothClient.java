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
    private volatile int clientSocket = WinsockNative.INVALID_SOCKET;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TransferListener listener;
    private JnaSocketOutputStream outputStream;
    private final Object lock = new Object();
    private volatile boolean isConnecting = false;

    public void setListener(TransferListener listener) {
        this.listener = listener;
    }

    public void connect(String addressStr) {
        synchronized (lock) {
            if (isConnecting) {
                System.out.println("[Client] 正在连接中，忽略重复请求");
                return;
            }
            if (clientSocket != WinsockNative.INVALID_SOCKET) {
                System.out.println("[Client] 已经连接，忽略请求");
                return;
            }
            isConnecting = true;
        }

        executor.submit(() -> {
            WinsockNative lib = WinsockNative.INSTANCE;
            int socketHandle = WinsockNative.INVALID_SOCKET;

            try {
                // 确保 Winsock 初始化
                WinsockNative.WSAData data = new WinsockNative.WSAData();
                lib.WSAStartup((short) 0x0202, data);

                socketHandle = lib.socket(WinsockNative.AF_BTH, WinsockNative.SOCK_STREAM, WinsockNative.BTHPROTO_RFCOMM);
                if (socketHandle == WinsockNative.INVALID_SOCKET) {
                    notifyError("创建客户端 Socket 失败");
                    resetConnectingState();
                    return;
                }

                SOCKADDR_BTH addr = new SOCKADDR_BTH();
                try {
                    addr.btAddr = Long.parseLong(addressStr);
                } catch (NumberFormatException e) {
                    notifyError("无效的地址格式");
                    lib.closesocket(socketHandle);
                    resetConnectingState();
                    return;
                }
                addr.port = 0;

                // 关键：指定服务 UUID (SPP)，让系统自动通过 SDP 查找对应端口
                addr.serviceClassId = new WinsockNative.GUID();
                // SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
                addr.serviceClassId.Data1 = 0x00001101;
                addr.serviceClassId.Data2 = (short) 0x0000;
                addr.serviceClassId.Data3 = (short) 0x1000;
                addr.serviceClassId.Data4 = new byte[] {
                        (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80,
                        (byte) 0x5F, (byte) 0x9B, (byte) 0x34, (byte) 0xFB
                };

                addr.write();

                System.out.println("[Client] 正在连接: " + addressStr + " (UUID: SPP)");
                
                // JNA: 在调用前清除错误，确保获取的是本次调用的错误
                Native.setLastError(0);
                int connectResult = lib.connect(socketHandle, addr, addr.size());
                System.out.println("[Client] connect 返回值: " + connectResult);
                
                if (connectResult == WinsockNative.SOCKET_ERROR) {
                    int errorCode = Native.getLastError();
                    if (errorCode == 0) {
                         errorCode = lib.WSAGetLastError();
                    }
                    
                    System.out.println("[Client] 连接失败，错误码: " + errorCode);
                    notifyError("连接失败: " + errorCode);
                    lib.closesocket(socketHandle);
                    resetConnectingState();
                    return;
                }

                System.out.println("[Client] Socket 连接成功，正在创建会话...");
                
                synchronized (lock) {
                    this.clientSocket = socketHandle;
                    this.isConnecting = false;
                }

                outputStream = new JnaSocketOutputStream(clientSocket);
                
                BluetoothSession session = new BluetoothSession(clientSocket, listener);
                if (listener != null) {
                    listener.onSessionCreated(session);
                }
                session.start();

                String code = BluetoothUtils.addressToCode(addr.btAddr);
                notifyConnection(true, code);

            } catch (Exception e) {
                e.printStackTrace();
                notifyError("连接异常: " + e.getMessage());
                if (socketHandle != WinsockNative.INVALID_SOCKET) {
                    lib.closesocket(socketHandle);
                }
                resetConnectingState();
            }
        });
    }

    private void resetConnectingState() {
        synchronized (lock) {
            isConnecting = false;
        }
    }

    // 废弃的方法，现在通过 Session 发送
    @Deprecated
    public void send(String message) throws IOException {
        // 兼容旧代码，但建议使用 Session
        if (clientSocket == WinsockNative.INVALID_SOCKET || outputStream == null) {
            throw new IOException("未连接");
        }
        System.out.println("[Client] 准备发送消息: " + message);
        byte[] packet = ProtocolWriter.createPacket(0L, "MSG", message.getBytes("UTF-8"));
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
        byte[] packet = ProtocolWriter.createPacket(0L, file.getName(), fileData);
        outputStream.write(packet);
        outputStream.flush();
    }

    public void close() {
        synchronized (lock) {
            if (clientSocket != WinsockNative.INVALID_SOCKET) {
                WinsockNative.INSTANCE.closesocket(clientSocket);
                clientSocket = WinsockNative.INVALID_SOCKET;
            }
            isConnecting = false;
        }
        // 尝试清理 Winsock
        try {
            WinsockNative.INSTANCE.WSACleanup();
        } catch (Throwable t) {
            // ignore
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
