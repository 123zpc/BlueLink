package com.bluelink.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 简易 AI 客户端
 * 兼容 Java 8，不依赖第三方库，用于调用 OpenAI 格式的 API
 */
public class SimpleAiClient {

    /**
     * 发送流式对话请求
     *
     * @param apiUrl     API 地址 (例如 https://api.deepseek.com/v1/chat/completions)
     * @param apiKey     API 密钥
     * @param prompt     用户输入
     * @param onChunk    收到文本片段的回调
     * @param onComplete 完成回调
     * @param onError    错误回调
     */
    public static void streamChat(String apiUrl, String apiKey, String prompt,
            Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // 1. 准备连接
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000); // AI 响应可能较慢，设置长一点

                // 2. 构造简易 JSON Body
                // 注意：这里手动拼接 JSON 以避免引入 Gson/Jackson 依赖，保持轻量
                // 需要简单的转义处理
                String escapedPrompt = escapeJson(prompt);
                String jsonBody = "{\n" +
                        "  \"model\": \"deepseek-chat\",\n" +
                        "  \"messages\": [{\"role\": \"user\", \"content\": \"" + escapedPrompt + "\"}],\n" +
                        "  \"stream\": true\n" +
                        "}";

                // 3. 发送请求
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 4. 检查状态码
                int code = conn.getResponseCode();
                if (code != 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder err = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null)
                            err.append(line);
                        onError.accept("API 请求失败 (" + code + "): " + err.toString());
                    } catch (Exception e) {
                        onError.accept("API 请求失败 (" + code + ")");
                    }
                    return;
                }

                // 5. 读取流式响应 (SSE)
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty())
                            continue;
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data))
                                break;

                            // 提取 content
                            // 格式通常是: data: {"id":..., "choices":[{"delta":{"content":"Hello"}}]}
                            String content = extractContent(data);
                            if (content != null && !content.isEmpty()) {
                                onChunk.accept(content);
                            }
                        }
                    }
                }

                onComplete.run();

            } catch (Exception e) {
                e.printStackTrace();
                onError.accept("连接异常: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * 极简 JSON 字符串转义
     */
    private static String escapeJson(String input) {
        if (input == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 处理控制字符
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * 从 SSE JSON 数据中提取 content 字段
     * 这是一个非常脆弱的解析器，仅用于 demo
     * 查找 "content":"..." 模式
     */
    private static String extractContent(String json) {
        try {
            String key = "\"content\":\"";
            int start = json.indexOf(key);
            if (start == -1)
                return null;

            start += key.length();
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;

            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    if (c == 'n')
                        sb.append('\n');
                    else if (c == 'r')
                        sb.append('\r');
                    else if (c == 't')
                        sb.append('\t');
                    else
                        sb.append(c);
                    escaped = false;
                } else {
                    if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break; // End of string
                    } else {
                        sb.append(c);
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
