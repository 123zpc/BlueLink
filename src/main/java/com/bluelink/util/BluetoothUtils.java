package com.bluelink.util;

import com.bluelink.net.jna.BluetoothApiNative;
import com.bluelink.net.jna.BluetoothApiNative.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝牙工具类
 * 提供本机蓝牙信息获取和设备发现功能
 */
public class BluetoothUtils {

    /**
     * 蓝牙设备信息
     */
    public static class BluetoothDevice {
        public final long address;
        public final String name;
        public final String code; // 6位连接码

        public BluetoothDevice(long address, String name) {
            this.address = address;
            this.name = name;
            this.code = addressToCode(address);
        }

        @Override
        public String toString() {
            return String.format("%s (%s) - %s", name, code, formatAddress(address));
        }
    }

    /**
     * 获取本机蓝牙适配器的 MAC 地址
     * 
     * @return 蓝牙 MAC 地址，如果没有蓝牙适配器返回 0
     */
    public static long getLocalBluetoothAddress() {
        try {
            BluetoothApiNative api = BluetoothApiNative.INSTANCE;

            BLUETOOTH_FIND_RADIO_PARAMS params = new BLUETOOTH_FIND_RADIO_PARAMS();
            PointerByReference phRadio = new PointerByReference();

            Pointer hFind = api.BluetoothFindFirstRadio(params, phRadio);
            if (hFind == null) {
                System.out.println("未找到蓝牙适配器");
                return 0;
            }

            try {
                Pointer hRadio = phRadio.getValue();
                if (hRadio != null) {
                    BLUETOOTH_RADIO_INFO radioInfo = new BLUETOOTH_RADIO_INFO();
                    int result = api.BluetoothGetRadioInfo(hRadio, radioInfo);
                    if (result == 0) { // ERROR_SUCCESS
                        return radioInfo.address;
                    }
                }
            } finally {
                api.BluetoothFindRadioClose(hFind);
            }
        } catch (Exception e) {
            System.out.println("获取蓝牙适配器信息失败: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 获取本机蓝牙适配器的名称
     */
    public static String getLocalBluetoothName() {
        try {
            BluetoothApiNative api = BluetoothApiNative.INSTANCE;

            BLUETOOTH_FIND_RADIO_PARAMS params = new BLUETOOTH_FIND_RADIO_PARAMS();
            PointerByReference phRadio = new PointerByReference();

            Pointer hFind = api.BluetoothFindFirstRadio(params, phRadio);
            if (hFind == null) {
                return "未知";
            }

            try {
                Pointer hRadio = phRadio.getValue();
                if (hRadio != null) {
                    BLUETOOTH_RADIO_INFO radioInfo = new BLUETOOTH_RADIO_INFO();
                    int result = api.BluetoothGetRadioInfo(hRadio, radioInfo);
                    if (result == 0) {
                        return radioInfo.getName();
                    }
                }
            } finally {
                api.BluetoothFindRadioClose(hFind);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未知";
    }

    /**
     * 扫描附近的蓝牙设备
     * 
     * @param timeoutSeconds 超时时间（秒），建议 10-15 秒
     * @return 发现的蓝牙设备列表
     */
    public static List<BluetoothDevice> discoverDevices(int timeoutSeconds) {
        List<BluetoothDevice> devices = new ArrayList<>();

        try {
            BluetoothApiNative api = BluetoothApiNative.INSTANCE;

            // 先获取蓝牙适配器句柄
            BLUETOOTH_FIND_RADIO_PARAMS radioParams = new BLUETOOTH_FIND_RADIO_PARAMS();
            PointerByReference phRadio = new PointerByReference();

            Pointer hFindRadio = api.BluetoothFindFirstRadio(radioParams, phRadio);
            if (hFindRadio == null) {
                System.out.println("未找到蓝牙适配器，无法扫描设备");
                return devices;
            }

            Pointer hRadio = phRadio.getValue();

            try {
                // 设置设备搜索参数
                BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams = new BLUETOOTH_DEVICE_SEARCH_PARAMS();
                searchParams.fReturnAuthenticated = true;
                searchParams.fReturnRemembered = true;
                searchParams.fReturnUnknown = true;
                searchParams.fReturnConnected = true;
                searchParams.fIssueInquiry = true; // 执行设备发现
                searchParams.cTimeoutMultiplier = (byte) Math.min(48, Math.max(1, timeoutSeconds / 1.28)); // 1.28秒 * 该值
                searchParams.hRadio = hRadio;

                BLUETOOTH_DEVICE_INFO deviceInfo = new BLUETOOTH_DEVICE_INFO();

                Pointer hFindDevice = api.BluetoothFindFirstDevice(searchParams, deviceInfo);
                if (hFindDevice != null) {
                    try {
                        do {
                            BluetoothDevice device = new BluetoothDevice(
                                    deviceInfo.Address,
                                    deviceInfo.getName());
                            devices.add(device);
                            System.out.println("发现设备: " + device);

                            // 重置 deviceInfo 用于下一次查询
                            deviceInfo = new BLUETOOTH_DEVICE_INFO();
                        } while (api.BluetoothFindNextDevice(hFindDevice, deviceInfo));
                    } finally {
                        api.BluetoothFindDeviceClose(hFindDevice);
                    }
                }
            } finally {
                api.BluetoothFindRadioClose(hFindRadio);
            }
        } catch (Exception e) {
            System.out.println("设备扫描失败: " + e.getMessage());
            e.printStackTrace();
        }

        return devices;
    }

    /**
     * 根据连接码查找设备
     * 
     * @param code    6位连接码
     * @param devices 设备列表
     * @return 匹配的设备，未找到返回 null
     */
    public static BluetoothDevice findDeviceByCode(String code, List<BluetoothDevice> devices) {
        if (code == null || code.length() != 6) {
            return null;
        }
        for (BluetoothDevice device : devices) {
            if (code.equals(device.code)) {
                return device;
            }
        }
        return null;
    }

    /**
     * 将蓝牙地址转换为 6 位连接码
     */
    public static String addressToCode(long address) {
        long code = Math.abs(address % 1_000_000);
        return String.format("%06d", code);
    }

    /**
     * 格式化蓝牙地址为可读字符串 (如 AA:BB:CC:DD:EE:FF)
     */
    public static String formatAddress(long address) {
        StringBuilder sb = new StringBuilder();
        for (int i = 5; i >= 0; i--) {
            if (sb.length() > 0)
                sb.append(":");
            sb.append(String.format("%02X", (address >> (i * 8)) & 0xFF));
        }
        return sb.toString();
    }
}
