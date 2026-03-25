package com.bluelink.util;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emoji 资源加载器
 * 负责本地缓存 Twemoji 图片，避免每次都从网络加载
 */
public class EmojiLoader {

    private static final String CACHE_DIR = System.getProperty("user.home") + "/.bluelink/emojis";
    private static final String BASE_URL = "https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/72x72/";
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(4);
    
    // 内存缓存：HexCode -> URL
    private static final java.util.Map<String, String> MEMORY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        File dir = new File(CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 获取 Emoji 的路径
     * 1. 优先检查内存缓存
     * 2. 检查 Classpath 资源 (打包在 JAR/EXE 中)
     * 3. 检查本地缓存目录 (file://...)
     * 4. 返回网络路径并触发异步下载
     */
    public static String getEmojiUrl(String hexCode) {
        // 0. 检查内存缓存
        if (MEMORY_CACHE.containsKey(hexCode)) {
            return MEMORY_CACHE.get(hexCode);
        }

        String resultUrl;

        // 1. 检查 Classpath (resources/emojis)
        URL resource = EmojiLoader.class.getResource("/emojis/" + hexCode + ".png");
        if (resource != null) {
            resultUrl = resource.toString();
        } else {
            // 2. 检查本地缓存
            File localFile = new File(CACHE_DIR, hexCode + ".png");
            if (localFile.exists()) {
                resultUrl = localFile.toURI().toString();
            } else {
                // 3. 本地不存在，返回网络 URL，同时触发下载以便下次使用
                String netUrl = BASE_URL + hexCode + ".png";
                downloadExecutor.submit(() -> downloadEmoji(hexCode, netUrl, localFile));
                resultUrl = netUrl;
            }
        }
        
        // 存入缓存
        MEMORY_CACHE.put(hexCode, resultUrl);
        return resultUrl;
    }

    private static void downloadEmoji(String hexCode, String urlString, File destination) {
        try {
            URL url = new URL(urlString);
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(destination)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            // 下载失败忽略，下次会再次尝试
            // System.err.println("Failed to download emoji: " + hexCode);
        }
    }
}
