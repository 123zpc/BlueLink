package com.bluelink.net.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Ws2_32.dll JNA 映射
 * 用于访问 Windows 原生 Socket API (特别是蓝牙 AF_BTH)
 */
public interface WinsockNative extends Library {
    // 关键：开启 JNA 的错误码捕获功能
    // 注意：不要使用 W32APIFunctionMapper，因为它会尝试给所有函数加 A/W 后缀，
    // 而 Ws2_32.dll 中 connect/send/recv 等核心函数没有后缀，会导致 UnsatisfiedLinkError。
    // 对于像 WSASetService 这样有后缀的函数，我们在接口中显式声明为 WSASetServiceA。
    
    WinsockNative INSTANCE = Native.load("Ws2_32", WinsockNative.class, 
        Collections.singletonMap(Library.OPTION_CALLING_CONVENTION, com.sun.jna.win32.StdCallLibrary.STDCALL_CONVENTION));

    // 常量定义
    int AF_BTH = 32;
    int SOCK_STREAM = 1;
    int BTHPROTO_RFCOMM = 3;
    int INVALID_SOCKET = -1;
    int SOCKET_ERROR = -1;

    // 结构体定义
    // 重要：Windows API 结构体通常是 1 字节对齐或 8 字节对齐，
    // SOCKADDR_BTH 在 WinSock2.h 中定义如下：
    // typedef struct _SOCKADDR_BTH {
    //     USHORT      addressFamily;  // 2 bytes
    //     BTH_ADDR    btAddr;         // 8 bytes (unsigned __int64)
    //     GUID        serviceClassId; // 16 bytes
    //     ULONG       port;           // 4 bytes
    // } SOCKADDR_BTH, *PSOCKADDR_BTH;
    // 总大小: 2 + 8 + 16 + 4 = 30 bytes
    // 但是，由于内存对齐，btAddr (8 bytes) 通常需要 8 字节对齐，导致 addressFamily 后面可能有 6 字节填充
    // 或者 #include <pshpack1.h> 强制 1 字节对齐。
    // 在 Windows SDK 中，SOCKADDR_BTH 是 #include <pshpack1.h> 的，所以是紧凑排列的 (1字节对齐)！
    
    @Structure.FieldOrder({ "AddressFamily", "btAddr", "serviceClassId", "port" })
    class SOCKADDR_BTH extends Structure {
        public short AddressFamily;
        public long btAddr;
        public GUID serviceClassId;
        public int port;

        public SOCKADDR_BTH() {
            this.AddressFamily = (short) AF_BTH;
            // 关键：Windows SDK 中 SOCKADDR_BTH 是 #include <pshpack1.h>，即 1 字节对齐
            setAlignType(Structure.ALIGN_NONE); 
        }

        // 覆盖 getFieldOrder 以确保顺序
        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("AddressFamily", "btAddr", "serviceClassId", "port");
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
     * WSAData 结构体
     */
    @Structure.FieldOrder({ "wVersion", "wHighVersion", "szDescription", "szSystemStatus", "iMaxSockets", "iMaxUdpDg", "lpVendorInfo" })
    class WSAData extends Structure {
        public short wVersion;
        public short wHighVersion;
        public byte[] szDescription = new byte[257];
        public byte[] szSystemStatus = new byte[129];
        public short iMaxSockets;
        public short iMaxUdpDg;
        public Pointer lpVendorInfo;
    }

    /**
     * 初始化 Winsock
     */
    int WSAStartup(short wVersionRequested, WSAData lpWSAData);

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
     * 获取最后的错误码
     * 注意：必须在失败后立即调用，且不能有其他 JNA 调用干扰
     */
    int WSAGetLastError();

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
     * 清理
     */
    int WSACleanup();

    // --- 服务注册相关 ---

    // 关键修正：Windows SDK 定义 RNRSERVICE_REGISTER 为 0
    int RNRSERVICE_REGISTER = 0; 
    int RNRSERVICE_DEREGISTER = 1;
    int RNRSERVICE_DELETE = 2;

    @Structure.FieldOrder({ "lpSockaddr", "iSockaddrLength" })
    public static class SOCKET_ADDRESS extends Structure {
        public Pointer lpSockaddr;
        public int iSockaddrLength;

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
            "dwNameSpace", "lpNSProviderId", "lpszContext", "dwNumberOfProtocols", "lpafpProtocols", "lpszQueryString", "dwNumberOfCsAddrs", "lpcsaBuffer", "dwOutputFlags", "lpBlob" })
    public static class WSAQUERYSET extends Structure {
        public int dwSize;
        public String lpszServiceInstanceName;
        public Pointer lpServiceClassId; // GUID*
        public Pointer lpVersion;
        public String lpszComment;
        public int dwNameSpace;
        public Pointer lpNSProviderId; // GUID*
        public String lpszContext;
        public int dwNumberOfProtocols;
        public Pointer lpafpProtocols; // LPAFPROTOCOLS
        public String lpszQueryString;
        public int dwNumberOfCsAddrs;
        public Pointer lpcsaBuffer; // CSADDR_INFO*
        public int dwOutputFlags;
        public Pointer lpBlob;

        public WSAQUERYSET() {
            // 关键：对齐方式必须与 OS 一致。默认 JNA 会尝试匹配，但有时候显式声明更好。
            // 另外，确保 size() 正确计算
            dwSize = size();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("dwSize", "lpszServiceInstanceName", "lpServiceClassId", "lpVersion", "lpszComment",
            "dwNameSpace", "lpNSProviderId", "lpszContext", "dwNumberOfProtocols", "lpafpProtocols", "lpszQueryString", "dwNumberOfCsAddrs", "lpcsaBuffer", "dwOutputFlags", "lpBlob");
        }
    }

    int NS_BTH = 16;

    /**
     * 注册服务
     * 注意：lpqs 必须是 WSAQUERYSET 的指针，或者 JNA Structure
     */
    int WSASetServiceA(WSAQUERYSET lpqs, int essOperation, int dwControlFlags);

    /**
     * 获取 Socket 名称
     */
    int getsockname(int s, Structure name, IntByReference namelen);
}
