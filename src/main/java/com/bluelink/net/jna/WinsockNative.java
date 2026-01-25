package com.bluelink.net.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.util.Arrays;
import java.util.List;

/**
 * Ws2_32.dll JNA 映射
 * 用于访问 Windows 原生 Socket API (特别是蓝牙 AF_BTH)
 */
public interface WinsockNative extends Library {
    WinsockNative INSTANCE = Native.load("Ws2_32", WinsockNative.class);

    // 常量定义
    int AF_BTH = 32;
    int SOCK_STREAM = 1;
    int BTHPROTO_RFCOMM = 3;
    int INVALID_SOCKET = -1;
    int SOCKET_ERROR = -1;

    // 结构体定义
    @Structure.FieldOrder({ "AddressFamily", "btAddr", "serviceClassId", "port", "dummy" })
    class SOCKADDR_BTH extends Structure {
        public short AddressFamily;
        public long btAddr; // BTH_ADDR is a 64-bit integer
        public GUID serviceClassId;
        public int port; // ULONG or ULONG associated with port
        public byte[] dummy = new byte[0]; // 只需要对齐

        public SOCKADDR_BTH() {
            this.AddressFamily = (short) AF_BTH;
        }
    }

    @Structure.FieldOrder({ "Data1", "Data2", "Data3", "Data4" })
    class GUID extends Structure {
        public int Data1;
        public short Data2;
        public short Data3;
        public byte[] Data4 = new byte[8];

        // 快捷构造常用 UUID
        public static GUID fromString(String uuidStr) {
            // 简化实现，实际项目可能需要完整的 UUID 解析逻辑
            // 这里仅作为结构体占位，具体赋值需根据业务逻辑
            return new GUID();
        }
    }

    // 核心函数映射

    /**
     * 初始化 Winsock
     */
    int WSAStartup(short wVersionRequested, Pointer lpWSAData);

    /**
     * 创建 Socket
     */
    int socket(int af, int type, int protocol);

    /**
     * 绑定
     */
    int bind(int s, Structure name, int namelen);

    /**
     * 监听
     */
    int listen(int s, int backlog);

    /**
     * 接受连接
     */
    int accept(int s, Structure addr, IntByReference addrlen);

    /**
     * 连接
     */
    int connect(int s, Structure name, int namelen);

    /**
     * 发送数据
     */
    int send(int s, byte[] buf, int len, int flags);

    /**
     * 接收数据
     */
    int recv(int s, byte[] buf, int len, int flags);

    /**
     * 关闭 Socket
     */
    int closesocket(int s);

    /**
     * 获取最后错误代码
     */
    int WSAGetLastError();

    /**
     * 清理
     */
    /**
     * 清理
     */
    int WSACleanup();

    // --- 服务注册相关 ---

    int RNRSERVICE_REGISTER = 1;
    int RNRSERVICE_DEREGISTER = 2;
    int RNRSERVICE_DELETE = 2;

    @Structure.FieldOrder({ "iSockaddrLength", "lpSockaddr", "iSocketType", "iProtocol" })
    public static class SOCKET_ADDRESS extends Structure {
        public int iSockaddrLength;
        public Pointer lpSockaddr;
        public int iSocketType;
        public int iProtocol;

        public SOCKET_ADDRESS() {
        }
    }

    @Structure.FieldOrder({ "LocalAddr", "RemoteAddr", "iSocketType", "iProtocol" })
    public static class CSADDR_INFO extends Structure {
        public SOCKET_ADDRESS LocalAddr;
        public SOCKET_ADDRESS RemoteAddr;
        public int iSocketType;
        public int iProtocol;

        public CSADDR_INFO() {
            LocalAddr = new SOCKET_ADDRESS();
            RemoteAddr = new SOCKET_ADDRESS();
        }
    }

    @Structure.FieldOrder({ "dwSize", "lpszServiceInstanceName", "lpServiceClassId", "lpVersion", "lpszComment",
            "dwNameSpace", "lpNSProviderId", "lpszContext", "dwNumberOfCsAddrs", "lpcsaBuffer", "lpBlob" })
    public static class WSAQUERYSET extends Structure {
        public int dwSize;
        public String lpszServiceInstanceName;
        public Pointer lpServiceClassId; // GUID*
        public Pointer lpVersion;
        public String lpszComment;
        public int dwNameSpace;
        public Pointer lpNSProviderId; // GUID*
        public String lpszContext;
        public int dwNumberOfCsAddrs;
        public Pointer lpcsaBuffer; // CSADDR_INFO*
        public Pointer lpBlob;

        public WSAQUERYSET() {
            dwSize = size();
        }
    }

    int NS_BTH = 16;

    /**
     * 注册服务
     */
    int WSASetService(WSAQUERYSET lpqs, int essOperation, int dwControlFlags);

    /**
     * 获取 Socket 名称信息 (getsockname)
     */
    int getsockname(int s, Structure name, IntByReference namelen);
}
