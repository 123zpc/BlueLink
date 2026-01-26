package com.bluelink.net;

import com.bluelink.net.jna.WinsockNative;
import com.bluelink.net.jna.WinsockNative.SOCKADDR_BTH;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.Native;

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
        System.out.println("[Server] runServer 线程启动");
        try {
            WinsockNative lib = WinsockNative.INSTANCE;
            
            // 0. 初始化 Winsock (保险起见)
            WinsockNative.WSAData wsaData = new WinsockNative.WSAData();
            if (lib.WSAStartup((short) 0x0202, wsaData) != 0) {
                 notifyError("WSAStartup 失败");
                 return;
            }

            // ...
            serverSocket = lib.socket(WinsockNative.AF_BTH, WinsockNative.SOCK_STREAM, WinsockNative.BTHPROTO_RFCOMM);
            if (serverSocket == WinsockNative.INVALID_SOCKET) {
                notifyError("创建服务端 Socket 失败: " + lib.WSAGetLastError());
                return;
            }

            // 3. Bind
            SOCKADDR_BTH addr = new SOCKADDR_BTH();
        addr.port = 7;
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
            sa.read(); // 必须读取 native 内存更新到 Java 对象
            System.out.println("[Server] getsockname 成功: port=" + sa.port + ", btAddr=" + sa.btAddr + ", len=" + saLen.getValue());
    
            WinsockNative.WSAQUERYSET qs = new WinsockNative.WSAQUERYSET();
            // 重新计算大小（虽然构造函数里有，但为了保险）
            // 注意：qs.write() 非常重要，它将 Java 对象的内存同步到本地内存
            qs.dwSize = qs.size();
            qs.lpszServiceInstanceName = "BlueLink Server";
            qs.lpszComment = "BlueLink Bluetooth Service";
            qs.dwNameSpace = lib.NS_BTH;
            qs.dwNumberOfCsAddrs = 1;
            qs.dwOutputFlags = 0; // 明确初始化
            
            // 关键：GUID 结构体内存布局
            // 我们定义一个 GUID 结构体实例，然后获取其指针
            WinsockNative.GUID spGuid = new WinsockNative.GUID();
            spGuid.Data1 = 0x00001101;
            spGuid.Data2 = 0x0000;
            spGuid.Data3 = 0x1000;
            spGuid.Data4 = new byte[] {
                (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80,
                (byte) 0x5F, (byte) 0x9B, (byte) 0x34, (byte) 0xFB
            };
            spGuid.write(); // 写入内存
            
            qs.lpServiceClassId = spGuid.getPointer();
    
            // 设置地址信息
            // 必须使用分配了内存的结构体，不能只是局部变量
            // 为了安全起见，我们可以使用 Memory 手动构建 CSADDR_INFO 数组
            // 但这里先尝试修正 CSADDR_INFO 的字段赋值
            
            WinsockNative.CSADDR_INFO csa = new WinsockNative.CSADDR_INFO();
            // iSocketType 和 iProtocol 必须为 0 或者正确的值
            // 某些文档显示在 WSASetService 中，这些值可能不需要，或者需要与 socket 创建时一致
            csa.iSocketType = WinsockNative.SOCK_STREAM;
            csa.iProtocol = WinsockNative.BTHPROTO_RFCOMM;
    
            // LocalAddr 设置
            csa.LocalAddr.iSockaddrLength = sa.size();
            // 必须重新写入 sa，确保指针指向的数据是最新的
            sa.write();
            csa.LocalAddr.lpSockaddr = sa.getPointer();
            
            // RemoteAddr 设置
            // 关键尝试：将 RemoteAddr 设为 NULL 或空结构体
            // 许多示例显示服务注册时 RemoteAddr 可以忽略，或者设为与 LocalAddr 一样
            // 如果设为一样报错，尝试设为空
            csa.RemoteAddr.iSockaddrLength = sa.size();
            csa.RemoteAddr.lpSockaddr = sa.getPointer();
            
            // 写入 csa
            csa.write();
            
            // 手动构建内存块来存储 CSADDR_INFO 数组
            // 因为 qs.lpcsaBuffer 需要一个指向 CSADDR_INFO 数组的指针
            // 虽然我们只有一个元素，但直接用结构体指针通常是可以的
            // 为了排除 JNA 结构体数组转换问题，我们直接用 csa.getPointer()
            qs.lpcsaBuffer = csa.getPointer();
            
            // 关键：lpBlob 必须为 NULL 或者指向有效数据
            qs.lpBlob = null; 
            
            // 再次检查 WSAQUERYSET 的其他字段
            qs.lpszContext = null; // 确保为空
            qs.lpVersion = null;   // 确保为空
            qs.lpNSProviderId = null; // 确保为空
            
            // 重要：在传递给本地函数前，必须将所有字段写入内存
            qs.write();
    
            // 注册服务
            Native.setLastError(0);
            if (lib.WSASetServiceA(qs, lib.RNRSERVICE_REGISTER, 0) == WinsockNative.SOCKET_ERROR) {
                int errorCode = Native.getLastError();
                if (errorCode == 0) errorCode = lib.WSAGetLastError();
                
                notifyError("注册服务失败: " + errorCode);
                // 不关闭，也许客户端能暴力连接？
            } else {
                System.out.println("蓝牙服务已注册 (UUID: SPP)");
            }
    
            if (listener != null) {
                listener.onConnectionStatusChanged(false, "服务端已启动，等待连接...");
            }
    
            // 5. Accept Loop
            System.out.println("[Server] 开始进入 Accept 循环");
            while (running) {
                SOCKADDR_BTH clientAddr = new SOCKADDR_BTH();
                IntByReference len = new IntByReference(clientAddr.size());
    
                System.out.println("[Server] 等待客户端连接 (accept)...");
                int clientSocket = lib.accept(serverSocket, clientAddr, len);
                if (clientSocket != WinsockNative.INVALID_SOCKET) {
                    clientAddr.read();
                    System.out.println("[Server] 接受到连接! Socket ID: " + clientSocket);
                    notifyConnection(true, com.bluelink.util.BluetoothUtils.addressToCode(clientAddr.btAddr));
    
                    // 处理客户端连接
                    executor.submit(() -> handleClient(clientSocket));
                } else {
                    if (running) {
                        // 只有在运行时出错才报错，关闭时出错忽略
                        // notifyError("Accept 失败: " + lib.WSAGetLastError());
                        System.out.println("[Server] accept 返回 INVALID_SOCKET, err=" + lib.WSAGetLastError());
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[Server] 严重错误: Server 线程崩溃");
            t.printStackTrace();
            notifyError("服务端崩溃: " + t.getMessage());
        }
    }

    private void handleClient(int clientSocket) {
        System.out.println("[Server] 开始处理客户端连接: " + clientSocket);
        try {
            BluetoothSession session = new BluetoothSession(clientSocket, listener);
            if (listener != null) {
                listener.onSessionCreated(session);
            }
            session.start();
            System.out.println("[Server] Session 启动成功");
        } catch (Exception e) {
            System.err.println("[Server] Session 启动失败: " + e.getMessage());
            e.printStackTrace();
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
        // 对应 runServer 中的 WSAStartup
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
