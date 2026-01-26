package com.bluelink.net.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Adler32;

/**
 * 协议写入器
 * 负责将消息或文件封装为协议数据包并进行压缩
 */
public class ProtocolWriter {

    private static final int MAGIC_NUMBER = 0xCAFEBABE; // 示例 Magic

    /**
     * 封装数据包
     * 结构: [Magic 4][SenderToken 8][NameLen 4][Name Var][OriginalSize 8][CompSize 8][CRC32 8][GZIP_Data Var]
     */
    public static byte[] createPacket(long senderToken, String name, byte[] data) throws IOException {
        System.out.println("[ProtocolWriter] 创建数据包: " + name + ", 数据大小: " + data.length);
        // 1. 压缩数据
        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedBaos)) {
            gzip.write(data);
        }
        byte[] compressedData = compressedBaos.toByteArray();

        // 2. 计算 CRC32 (基于原始数据)
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long crcValue = crc32.getValue();

        // 3. 构建包头和包体
        ByteArrayOutputStream finalBaos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(finalBaos);

        dos.writeInt(MAGIC_NUMBER); // Magic
        dos.writeLong(senderToken); // Sender Token (NEW)
        byte[] nameBytes = name.getBytes("UTF-8");
        dos.writeInt(nameBytes.length); // NameLen
        dos.write(nameBytes); // Name
        dos.writeLong(data.length); // OriginalSize
        dos.writeLong(compressedData.length); // CompressedSize
        dos.writeLong(crcValue); // CRC32
        dos.write(compressedData); // GZIP_Data

        return finalBaos.toByteArray();
    }
}
