package com.bluelink.util;

/**
 * 本机码生成工具
 * 基于蓝牙 MAC 地址生成 6 位连接码
 */
public class MdCodeUtil {

    /**
     * 获取本机唯一 6 位数字码 (基于蓝牙 MAC 地址)
     * 逻辑: 蓝牙_MAC_Long % 1,000,000 -> 6-digit String
     */
    public static String getMyCode() {
        try {
            long btAddress = BluetoothUtils.getLocalBluetoothAddress();
            if (btAddress != 0) {
                return BluetoothUtils.addressToCode(btAddress);
            }
        } catch (Exception e) {
            System.out.println("获取蓝牙地址失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 如果没有蓝牙适配器，显示特殊提示
        return "------";
    }

    /**
     * 获取本机蓝牙地址（完整）
     */
    public static long getMyBluetoothAddress() {
        try {
            return BluetoothUtils.getLocalBluetoothAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 获取本机蓝牙名称
     */
    public static String getMyBluetoothName() {
        try {
            return BluetoothUtils.getLocalBluetoothName();
        } catch (Exception e) {
            return "未知";
        }
    }
}
