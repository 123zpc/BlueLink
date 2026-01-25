package com.bluelink.net.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

/**
 * Windows Bluetooth API JNA 映射
 * 用于访问本机蓝牙适配器信息和设备发现
 * 
 * 加载 BluetoothApis.dll (Windows Vista+)
 */
public interface BluetoothApiNative extends Library {
    BluetoothApiNative INSTANCE = Native.load("BluetoothApis", BluetoothApiNative.class);

    // ==================== 结构体定义 ====================

    /**
     * BLUETOOTH_FIND_RADIO_PARAMS
     */
    @Structure.FieldOrder({ "dwSize" })
    class BLUETOOTH_FIND_RADIO_PARAMS extends Structure {
        public int dwSize;

        public BLUETOOTH_FIND_RADIO_PARAMS() {
            dwSize = size();
        }
    }

    /**
     * BLUETOOTH_RADIO_INFO
     */
    @Structure.FieldOrder({ "dwSize", "address", "szName", "ulClassofDevice", "lmpSubversion", "manufacturer" })
    class BLUETOOTH_RADIO_INFO extends Structure {
        public int dwSize;
        public long address; // BTH_ADDR (蓝牙 MAC 地址)
        public char[] szName = new char[248]; // BLUETOOTH_MAX_NAME_SIZE
        public int ulClassofDevice;
        public short lmpSubversion;
        public short manufacturer;

        public BLUETOOTH_RADIO_INFO() {
            dwSize = size();
        }

        public String getName() {
            return Native.toString(szName);
        }
    }

    /**
     * BLUETOOTH_DEVICE_SEARCH_PARAMS
     */
    @Structure.FieldOrder({ "dwSize", "fReturnAuthenticated", "fReturnRemembered",
            "fReturnUnknown", "fReturnConnected", "fIssueInquiry",
            "cTimeoutMultiplier", "hRadio" })
    class BLUETOOTH_DEVICE_SEARCH_PARAMS extends Structure {
        public int dwSize;
        public boolean fReturnAuthenticated;
        public boolean fReturnRemembered;
        public boolean fReturnUnknown;
        public boolean fReturnConnected;
        public boolean fIssueInquiry; // 是否执行设备发现
        public byte cTimeoutMultiplier; // 超时倍数 (1.28秒 * 该值)
        public Pointer hRadio; // 蓝牙适配器句柄

        public BLUETOOTH_DEVICE_SEARCH_PARAMS() {
            dwSize = size();
        }
    }

    /**
     * BLUETOOTH_DEVICE_INFO
     */
    @Structure.FieldOrder({ "dwSize", "Address", "ulClassofDevice", "fConnected",
            "fRemembered", "fAuthenticated", "stLastSeen", "stLastUsed", "szName" })
    class BLUETOOTH_DEVICE_INFO extends Structure {
        public int dwSize;
        public long Address; // BTH_ADDR
        public int ulClassofDevice;
        public boolean fConnected;
        public boolean fRemembered;
        public boolean fAuthenticated;
        public SYSTEMTIME stLastSeen;
        public SYSTEMTIME stLastUsed;
        public char[] szName = new char[248];

        public BLUETOOTH_DEVICE_INFO() {
            dwSize = size();
        }

        public String getName() {
            return Native.toString(szName);
        }
    }

    /**
     * SYSTEMTIME 结构体
     */
    @Structure.FieldOrder({ "wYear", "wMonth", "wDayOfWeek", "wDay",
            "wHour", "wMinute", "wSecond", "wMilliseconds" })
    class SYSTEMTIME extends Structure {
        public short wYear;
        public short wMonth;
        public short wDayOfWeek;
        public short wDay;
        public short wHour;
        public short wMinute;
        public short wSecond;
        public short wMilliseconds;
    }

    // ==================== API 函数 ====================

    /**
     * 查找第一个蓝牙适配器
     * 
     * @return 查找句柄，如果失败返回 null
     */
    Pointer BluetoothFindFirstRadio(BLUETOOTH_FIND_RADIO_PARAMS pbtfrp, PointerByReference phRadio);

    /**
     * 查找下一个蓝牙适配器
     */
    boolean BluetoothFindNextRadio(Pointer hFind, PointerByReference phRadio);

    /**
     * 关闭蓝牙适配器查找句柄
     */
    boolean BluetoothFindRadioClose(Pointer hFind);

    /**
     * 获取蓝牙适配器信息
     */
    int BluetoothGetRadioInfo(Pointer hRadio, BLUETOOTH_RADIO_INFO pRadioInfo);

    /**
     * 查找第一个蓝牙设备
     */
    Pointer BluetoothFindFirstDevice(BLUETOOTH_DEVICE_SEARCH_PARAMS pbtsp, BLUETOOTH_DEVICE_INFO pbtdi);

    /**
     * 查找下一个蓝牙设备
     */
    boolean BluetoothFindNextDevice(Pointer hFind, BLUETOOTH_DEVICE_INFO pbtdi);

    /**
     * 关闭设备查找句柄
     */
    boolean BluetoothFindDeviceClose(Pointer hFind);
}
