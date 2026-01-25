# BlueLink - 蓝牙离线传输与聊天工具

BlueLink 是一款基于 **Windows Native Bluetooth API** (Winsock/RFCOMM) 开发的现代化即时通讯与文件传输工具。它专为 **无网络环境** 设计，无需 Wi-Fi 或互联网，仅需电脑具备蓝牙功能即可实现高速、安全的点对点通信。

## 🌟 核心特性

- **📡 蓝牙直连 (No Network Needed)**: 基于蓝牙 RFCOMM 协议构建，完全脱离路由器和互联网，在断网环境下依然可用。
- **⚡ 私有通信协议**: 采用自定义的二级制数据包协议，支持自动分包、粘包处理与校验，确保数据传输的完整性与稳定性。
- **🔍 自动设备发现**: 利用 Windows 本地蓝牙栈自动扫描周围可见设备，即点即连。
- **💬 即时通讯**: 支持低延迟文本聊天，消息即发即达。
- **📂 极速文件传输**: 支持拖拽发送任意格式文件，无文件大小限制。
- **🛡️ 隐私安全**: 真正的一对一物理链路通信，数据仅在两台设备间传输，不仅不经过云端，甚至不经过局域网网关。

## 🛠️ 技术架构

本项目由 **ZPC** 独立开发，于 **2026年1月25日** 发布，如有疑问可邮箱联系privacyporton@proton.me。

核心技术栈：
- **通信层**: JNA (Java Native Access) 调用 Windows `ws2_32.dll` 与 `irprops.cpl`，直接操作底层 Bluetooth Socket。
- **协议层**: 自研二进制流协议 (Header + Payload)，基于 RFCOMM 信道实现可靠传输。
- **界面层**: Java Swing + FlatLaf + MigLayout，提供丝滑的现代化交互体验。
- **数据层**: H2 Embedded Database

### 目录结构说明

```
src/main/java/com/bluelink/
├── 🚀 Main.java                 // 程序入口
├── 🖥️ ui/                       // 界面层
│   ├── ModernQQFrame.java      // 主窗口框架
│   └── bubble/                 // 聊天气泡渲染
├── 🌐 net/                      // 蓝牙网络核心层
│   ├── jna/                    // Windows Native API 映射 (Winsock)
│   ├── protocol/               // 自定义通信协议解析器
│   ├── BluetoothServer.java    // RFCOMM 服务端
│   └── BluetoothClient.java    // RFCOMM 客户端
└── 🔧 util/                     // 工具类
```

## 📦 安装与运行

### 方式一：安装包 (Windows)
前往 [Releases](https://github.com/123zpc/BlueLink/releases) 页面下载最新的 `.msi` 安装包。

*注意：本软件仅支持 Windows 10/11 且需要设备支持蓝牙功能。*

### 方式二：源码构建
```bash
git clone https://github.com/123zpc/BlueLink.git
cd BlueLink
mvn clean package
```

## 📄 许可证

本项目按 MIT 许可证开源。

---
**Author**: ZPC  
**Date**: 2026-01-25
