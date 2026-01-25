# BlueLink - 局域网传输与聊天工具

BlueLink 是一款现代、轻量级的 Java 应用程序，专为局域网 (LAN) 内的无缝文件传输和即时通讯而设计。它采用了类似现代 IM 客户端 (如 QQ NT) 的界面风格，专注于隐私和设备间的直接通信，无需外部服务器。

## 功能特性

- **局域网发现**: 自动扫描并连接同一网络下的其他 BlueLink 设备。
- **即时通讯**: 支持即时文本聊天，包含发送/接收状态反馈。
- **文件传输**: 支持拖拽发送文件，具备传输记录和历史管理功能。
- **跨平台**: 基于 Java Swing/FlatLaf 构建，支持 Windows, macOS 和 Linux。
- **安全隐私**: 采用直接 Socket 连接，数据不经过云端，确保隐私安全。
- **现代化 UI**: 简洁流畅的界面，支持高分屏 (HiDPI)。
- **安全设置**: 配置更改具备暂存和确认机制，防止误操作导致设置丢失。

## 安装指南

### Windows 用户
前往 [Releases](https://github.com/123zpc/BlueLink/releases) 页面下载最新的 `.msi` 或 `.exe` 安装包进行安装。

### 手动运行 (需要 Java 环境)
1. 确保已安装 JDK 17 或更高版本。
2. 下载 `BlueLink-1.0-SNAPSHOT.jar`。
3. 运行命令: `java -jar BlueLink-1.0-SNAPSHOT.jar`

## 源码构建

环境要求: JDK 17+, Maven.

```bash
git clone https://github.com/123zpc/BlueLink.git
cd BlueLink
mvn clean package
```

构建完成后，可执行的 JAR 文件位于 `target/` 目录下。

## 贡献

欢迎提交 Pull Request。对于重大更改，请先提交 Issue 讨论您想要改变的内容。

## 许可证

[MIT](LICENSE)
