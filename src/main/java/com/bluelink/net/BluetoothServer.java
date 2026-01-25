package com.bluelink.net;

import com.bluelink.net.jna.WinsockNative;
import com.bluelink.net.jna.WinsockNative.SOCKADDR_BTH;
import com.sun.jna.ptr.IntByReference;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 蓝牙服务端
 * 使用 JNA 调用 Windows Socket API (Winsock)
 */
public class BluetoothServer {
    private volatile boolean running = false;
    private int serverSocket = WinsockNative.INVALID_SOCKET;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TransferListener listener;

    public void setListener(TransferListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running)
            return;
        running = true;
        executor.submit(this::runServer);
    }

    private void runServer() {
        WinsockNative lib = WinsockNative.INSTANCE;

        // 1. WSAStartup (虽然 Java Socket 也会初始化，但手动调用是个好习惯确保环境)
        // 实际上 JNA 加载 DLL 时通常不需要显式调用 WSAStartup 如果之前已经有 Java Net 初始化，
        // 但为了保险起见，这里忽略也可以，或者做一下。
        // 为简化，假设环境 Ready。

        // 2. 创建 Socket
        serverSocket = lib.socket(WinsockNative.AF_BTH, WinsockNative.SOCK_STREAM, WinsockNative.BTHPROTO_RFCOMM);
        if (serverSocket == WinsockNative.INVALID_SOCKET) {
            notifyError("创建服务端 Socket 失败: " + lib.WSAGetLastError());
            return;
        }

        // 3. Bind
        SOCKADDR_BTH addr = new SOCKADDR_BTH();
        addr.port = 0; // 让系统分配
        // 这里需要注意 JNA Structure 的使用
        addr.write();

        if (lib.bind(serverSocket, addr, addr.size()) == WinsockNative.SOCKET_ERROR) {
            notifyError("绑定端口失败: " + lib.WSAGetLastError());
            close();
            return;
        }

        if (lib.listen(serverSocket, 5) == WinsockNative.SOCKET_ERROR) {
            notifyError("监听失败: " + lib.WSAGetLastError());
            close();
            return;
        }

        // 4.5 注册服务 (SDP)
        // 关键步骤：必须注册 UUID，手机才能通过 SPP 协议找到并连接
        // 使用标准 SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
        WinsockNative.SOCKADDR_BTH sa = new WinsockNative.SOCKADDR_BTH();
        IntByReference saLen = new IntByReference(sa.size());
        if (lib.getsockname(serverSocket, sa, saLen) == WinsockNative.SOCKET_ERROR) {
            notifyError("获取端口失败: " + lib.WSAGetLastError());
            close();
            return;
        }

        WinsockNative.WSAQUERYSET qs = new WinsockNative.WSAQUERYSET();
        qs.dwSize = qs.size();
        qs.lpszServiceInstanceName = "BlueLink Server";
        qs.lpszComment = "BlueLink Bluetooth Service";
        qs.dwNameSpace = lib.NS_BTH;
        qs.dwNumberOfCsAddrs = 1;

        // 设置 UUID (SPP)
        qs.lpServiceClassId = new com.sun.jna.Memory(16);
        // SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
        // Windows 也是小端字节序
        byte[] uuidBytes = new byte[] {
                (byte) 0x01, (byte) 0x11, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x10, (byte) 0x80, (byte) 0x00,
                (byte) 0x00, (byte) 0x80, (byte) 0x5F, (byte) 0x9B,
                (byte) 0x34, (byte) 0xFB
        };
        // 修正 JNA 内存写入
        // UUID 结构：Data1(4), Data2(2), Data3(2), Data4(8)
        // 00001101 -> Data1 (int)
        // 0000 -> Data2 (short)
        // 1000 -> Data3 (short)
        // 8000-00805F9B34FB -> Data4 (byte[8])

        // 简单直接写入字节流（注意字节序）
        // GUID: {00001101-0000-1000-8000-00805F9B34FB}
        // Memory 布局：
        // 0-3: Data1 (0x00001101) -> 小端: 01 11 00 00
        // 4-5: Data2 (0x0000) -> 00 00
        // 6-7: Data3 (0x1000) -> 00 10
        // 8-15: Data4 (80 00 00 80 5F 9B 34 FB)

        qs.lpServiceClassId.setInt(0, 0x00001101);
        qs.lpServiceClassId.setShort(4, (short) 0x0000);
        qs.lpServiceClassId.setShort(6, (short) 0x1000);
        qs.lpServiceClassId.setByte(8, (byte) 0x80);
        qs.lpServiceClassId.setByte(9, (byte) 0x00);
        qs.lpServiceClassId.setByte(10, (byte) 0x00);
        qs.lpServiceClassId.setByte(11, (byte) 0x80);
        qs.lpServiceClassId.setByte(12, (byte) 0x5F);
        qs.lpServiceClassId.setByte(13, (byte) 0x9B);
        qs.lpServiceClassId.setByte(14, (byte) 0x34);
        qs.lpServiceClassId.setByte(15, (byte) 0xFB);

        // 设置地址信息
        WinsockNative.CSADDR_INFO csa = new WinsockNative.CSADDR_INFO();
        csa.iSocketType = WinsockNative.SOCK_STREAM;
        csa.iProtocol = WinsockNative.BTHPROTO_RFCOMM;

        csa.LocalAddr.iSockaddrLength = sa.size();
        csa.LocalAddr.lpSockaddr = sa.getPointer();
        csa.LocalAddr.iSocketType = WinsockNative.SOCK_STREAM;
        csa.LocalAddr.iProtocol = WinsockNative.BTHPROTO_RFCOMM;

        // 我们不需要 RemoteAddr，但需要分配内存
        // JNA Structure 需要 write()
        sa.write();
        csa.write();

        // CSADDR_INFO 数组指针
        qs.lpcsaBuffer = csa.getPointer();

        // 注册服务
        if (lib.WSASetService(qs, lib.RNRSERVICE_REGISTER, 0) == WinsockNative.SOCKET_ERROR) {
            notifyError("注册服务失败: " + lib.WSAGetLastError());
            // 不关闭，也许客户端能暴力连接？
        } else {
            System.out.println("蓝牙服务已注册 (UUID: SPP)");
        }

        if (listener != null) {
            listener.onConnectionStatusChanged(true, "服务端已启动，等待连接...");
        }

        // 5. Accept Loop
        while (running) {
            SOCKADDR_BTH clientAddr = new SOCKADDR_BTH();
            IntByReference len = new IntByReference(clientAddr.size());

            int clientSocket = lib.accept(serverSocket, clientAddr, len);
            if (clientSocket != WinsockNative.INVALID_SOCKET) {
                clientAddr.read();
                notifyConnection(true, "客户端: " + clientAddr.btAddr); // 这里简单显示地址，实际可能需要解析

                // 处理客户端连接
                executor.submit(() -> handleClient(clientSocket));
            } else {
                if (running) {
                    // 只有在运行时出错才报错，关闭时出错忽略
                    // notifyError("Accept 失败: " + lib.WSAGetLastError());
                }
            }
        }
    }

    private void handleClient(int clientSocket) {
        WinsockNative lib = WinsockNative.INSTANCE;
        try (com.bluelink.net.jna.JnaSocketInputStream jis = new com.bluelink.net.jna.JnaSocketInputStream(
                clientSocket);
                java.io.DataInputStream dis = new java.io.DataInputStream(jis)) {

            while (running) {
                try {
                    com.bluelink.net.protocol.ProtocolReader.Packet packet = com.bluelink.net.protocol.ProtocolReader
                            .readPacket(dis);
                    if (packet == null) {
                        break; // 连接断开
                    }

                    // 判断 packet 类型
                    // 目前 ProtocolReader 简单的返回了 name 和 data
                    // 我们可以通过 name 来判断。例如:
                    // 如果 name 是 "MSG:UUID", 则 data 是文本内容 UTF-8
                    // 如果 name 是 "filename.txt", 则 data 是文件内容
                    // 为了区分，我们在发送时制定一种约定。

                    // 约定:
                    // 文本消息: name = "MSG" (实际上 ProtocolWriter 的 name 字段可以用来传文件名或特殊标识)
                    // 文件消息: name = "filename.ext"

                    if ("MSG".equals(packet.name)) {
                        String text = new String(packet.data, "UTF-8");
                        if (listener != null)
                            listener.onMessageReceived("Remote", text);
                    } else {
                        // 这是一个文件
                        // 保存到本地
                        java.io.File downloadDir = new java.io.File(com.bluelink.util.AppConfig.getDownloadPath());
                        if (!downloadDir.exists())
                            downloadDir.mkdirs();

                        java.io.File file = new java.io.File(downloadDir, packet.name);
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            fos.write(packet.data);
                        }

                        if (listener != null)
                            listener.onFileReceived("Remote", file);
                    }

                } catch (java.io.IOException e) {
                    // 读取错误或断开
                    break;
                }
            }
        } catch (Exception e) {
            notifyError("客户端处理异常: " + e.getMessage());
        } finally {
            lib.closesocket(clientSocket);
        }
    }

    public void stop() {
        running = false;
        close();
    }

    private void close() {
        if (serverSocket != WinsockNative.INVALID_SOCKET) {
            WinsockNative.INSTANCE.closesocket(serverSocket);
            serverSocket = WinsockNative.INVALID_SOCKET;
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
