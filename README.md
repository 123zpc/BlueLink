# BlueLink - Cross-Platform LAN Transfer & Chat

BlueLink is a modern, lightweight Java application designed for seamless file transfer and instant messaging over a Local Area Network (LAN). It mimics the familiar style of modern IM clients (like QQ NT) but focuses on privacy and direct device-to-device communication without external servers.

## Features

- **LAN Discovery**: Automatically scan for and connect to other BlueLink devices on the same network.
- **Instant Messaging**: Real-time text chat with support for send/receive status.
- **File Transfer**: Drag-and-drop file sharing with transfer progress and history logging.
- **Cross-Platform**: Built with Java Swing/FlatLaf, runs on Windows, macOS, and Linux.
- **Secure & Private**: Direct socket connections; no cloud storage involved.
- **Modern UI**: Clean, responsive interface with Dark Mode support (via system theme) and high-DPI scaling.
- **Safe Settings**: Configuration changes are guarded with confirmation logic to prevent accidental loss.

## Installation

### Windows
Download the latest `.msi` or `.exe` installer from the [Releases](https://github.com/123zpc/BlueLink/releases) page.

### Manual Run (Java Required)
1. Ensure JDK 17+ is installed.
2. Download the `BlueLink-1.0-SNAPSHOT.jar`.
3. Run: `java -jar BlueLink-1.0-SNAPSHOT.jar`

## Building from Source

Requirements: JDK 17+, Maven.

```bash
git clone https://github.com/123zpc/BlueLink.git
cd BlueLink
mvn clean package
```

The executable JAR will be in the `target/` directory.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License

[MIT](LICENSE)
