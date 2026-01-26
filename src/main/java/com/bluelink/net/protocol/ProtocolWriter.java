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
     * 结构: [Magic 4][NameLen 4][Name Var][OriginalSize 8][CRC32 8][GZIP_Data Var]
     * 注意：为了简化，这里先在内存中构建完整包，大文件传输时应流式处理。
     * 但考虑到 JNA send 接口通常处理 byte[]，这里先实现 byte[] 生成。
     */
    public static byte[] createPacket(String name, byte[] data) throws IOException {
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
        byte[] nameBytes = name.getBytes("UTF-8");
        dos.writeInt(nameBytes.length); // NameLen
        dos.write(nameBytes); // Name
        dos.writeLong(data.length); // OriginalSize
        dos.writeLong(compressedData.length); // CompressedSize (Fix: Missing in previous version)
        dos.writeLong(crcValue); // CRC32
        dos.write(compressedData); // GZIP_Data

        return finalBaos.toByteArray();
    }
}
