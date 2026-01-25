package com.bluelink;

import com.bluelink.util.MdCodeUtil;
import com.bluelink.net.jna.WinsockNative;

/**
 * 控制台验证入口
 * 用于测试非 UI 逻辑，如本机码生成、DLL 加载等
 */
public class ConsoleMain {
    public static void main(String[] args) {
        System.out.println("BlueLink 核心引擎验证启动...");

        // 1. 测试本机码
        String myCode = MdCodeUtil.getMyCode();
        System.out.println("本机机器码: " + myCode);

        // 2. 测试 JNA 加载
        try {
            // 尝试调用一个简单的函数，例如 WSAGetLastError (如果未初始化可能返回特定值或0)
            // 主要是看是否抛出 UnsatisfiedLinkError
            int err = WinsockNative.INSTANCE.WSAGetLastError();
            System.out.println("Winsock DLL 加载成功. 初始错误状态: " + err);
        } catch (Throwable t) {
            System.err.println("Winsock DLL 加载失败: " + t.getMessage());
            t.printStackTrace();
        }

        System.out.println("验证完成.");
    }
}
