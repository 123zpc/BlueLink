package com.bluelink.net.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/**
 * 协议读取器
 * 负责解析协议数据包并解压
 */
public class ProtocolReader {

    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static class Packet {
        public String name;
        public byte[] data;
    }

    /**
     * 从流中读取并解析下一个数据包
     * 
     * @param dis 数据输入流
     * @return 解析出的 Packet，如果流结束则返回 null
     */
    public static Packet readPacket(DataInputStream dis) throws IOException {
        // 1. 读取 Magic
        int magic;
        try {
            magic = dis.readInt();
            System.out.println("[Protocol] Magic 读取成功: " + Integer.toHexString(magic));
        } catch (IOException e) {
            System.out.println("[Protocol] Magic 读取失败 (可能是连接关闭): " + e.getMessage());
            return null; // Stream ended
        }

        if (magic != MAGIC_NUMBER) {
            throw new IOException("无效的协议魔数: " + Integer.toHexString(magic));
        }

        // 2. 读取名称
        int nameLen = dis.readInt();
        System.out.println("[Protocol] NameLen: " + nameLen);
        byte[] nameBytes = new byte[nameLen];
        dis.readFully(nameBytes);
        String name = new String(nameBytes, "UTF-8");
        System.out.println("[Protocol] Name: " + name);

        // 3. 读取大小和 CRC
        long originalSize = dis.readLong();
        long compressedSize = dis.readLong(); // NEW
        long receivedCrc = dis.readLong();
        System.out.println(String.format("[Protocol] Header: OrigSize=%d, CompSize=%d, CRC=%d", originalSize, compressedSize, receivedCrc));

        // 4. 读取压缩数据
        // 安全检查: 防止 OOM
        if (compressedSize > 100 * 1024 * 1024) { // 100MB max per packet for safety
            throw new IOException("数据包过大: " + compressedSize);
        }

        byte[] compressedData = new byte[(int) compressedSize];
        dis.readFully(compressedData);
        System.out.println("[Protocol] Body 读取完成");

        // 5. 解压
        ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                decompressedBaos.write(buffer, 0, len);
            }
        }
        byte[] originalData = decompressedBaos.toByteArray();

        // 6. 校验大小
        if (originalData.length != originalSize) {
            throw new IOException("数据大小不匹配. 期望: " + originalSize + ", 实际: " + originalData.length);
        }

        // 7. 校验 CRC
        CRC32 crc32 = new CRC32();
        crc32.update(originalData);
        if (crc32.getValue() != receivedCrc) {
            throw new IOException("CRC 校验失败");
        }

        Packet packet = new Packet();
        packet.name = name;
        packet.data = originalData;
        return packet;
    }
}
