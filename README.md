# BlueLink - 局域网传输与聊天工具

BlueLink 是一款现代、轻量级的 Java 应用程序，专为局域网 (LAN) 内的无缝文件传输和即时通讯而设计。它采用了类似现代 IM 客户端 (如 QQ NT) 的界面风格，专注于隐私和设备间的直接通信，无需外部服务器。

## 🌟 核心特性

- **局域网发现**: 自动扫描并连接同一网络下的其他 BlueLink 设备，无需手动输入 IP。
- **即时通讯**: 支持低延迟的文本聊天，包含发送/接收状态反馈。
- **极速文件传输**: 支持拖拽发送文件，大文件分块传输，具备完善的传输记录。
- **跨平台**: 基于 Java Swing/FlatLaf 构建，完美支持 Windows, macOS 和 Linux。
- **安全隐私**: 采用直接 Socket 点对点连接，数据不经过任何云端服务器。
- **现代化 UI**: 简洁流畅的界面，支持高分屏 (HiDPI) 和深色模式适配。
- **安全设置**: 配置更改具备暂存和确认机制，防止误操作。

## 🛠️ 技术架构与代码结构

本项目基于 Java 1.8+ 开发 (推荐 JDK 17+)，采用 Maven 进行依赖管理和构建。核心技术栈包括：
- **GUI**: Swing + FlatLaf (主题) + MigLayout (布局)
- **网络**: Java Socket IO + JNA (Windows Native Bluetooth/Socket 优化)
- **数据库**: H2 Database (嵌入式存储聊天记录)

### 目录结构说明

```
src/main/java/com/bluelink/
├── 🚀 Main.java                 // 程序入口
├── 🖥️ ui/                       // 界面层
│   ├── ModernQQFrame.java      // 主窗口框架 (核心 UI 逻辑)
│   ├── ConnectionPanel.java    // 连接与扫描面板
│   ├── CodeInputPanel.java     // 验证码输入组件
│   ├── TrayManager.java        // 系统托盘管理
│   └── bubble/                 // 聊天气泡渲染组件
├── 🌐 net/                      // 网络层
│   ├── BluetoothServer.java    // 服务端 (接收连接)
│   ├── BluetoothClient.java    // 客户端 (发起连接)
│   ├── protocol/               // 自定义通信协议 (消息头/数据包)
│   └── jna/                    // JNA 本地接口调用
├── 💾 db/                       // 数据持久层
│   ├── DatabaseManager.java    // H2 数据库连接管理
│   └── TransferDao.java        // 聊天记录 CRUD 操作
└── 🔧 util/                     // 工具类
    ├── AppConfig.java          // 配置管理 (config.properties)
    ├── UiUtils.java            // 全局 UI 样式工具
    └── MdCodeUtil.java         // 机器码生成逻辑
```

## 📦 安装与运行

### 方式一：安装包 (Windows)
前往 [Releases](https://github.com/123zpc/BlueLink/releases) 页面下载最新的 `.msi` 安装包，双击安装即可。

### 方式二：手动运行 (任意平台)
1. 确保已安装 JDK 17 或更高版本。
2. 下载 release 中的 `BlueLink-1.0-SNAPSHOT.jar`。
3. 运行命令:
   ```bash
   java -jar BlueLink-1.0-SNAPSHOT.jar
   ```

## 🔨 源码构建

如果您想自己编译项目，请按照以下步骤操作：

### 环境要求
- JDK 17 或更高版本
- Maven 3.6+

### 构建步骤
```bash
# 1. 克隆仓库
git clone https://github.com/123zpc/BlueLink.git
cd BlueLink

# 2. 编译打包
mvn clean package
```
构建成功后，可执行文件位于 `target/` 目录下。

### 生成 Windows 安装包
本项目使用 GitHub Actions 自动构建部署。如果您需要在本地生成 MSI 安装包，需要安装 `WiX Toolset` 并运行 `jpackage` 命令 (参考 `.github/workflows/release.yml`)。

## 🤝 贡献指南

欢迎提交 Pull Request (PR) 或 Issue！
1. Fork 本仓库。
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)。
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)。
4. 推送到分支 (`git push origin feature/AmazingFeature`)。
5. 开启一个 Pull Request。

## 📄 许可证

[MIT](LICENSE) © 2024 BlueLink Team
