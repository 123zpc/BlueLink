package com.bluelink.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RedisChatMemory implements ChatMemory {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String KEY_PREFIX = "chat:memory:";
    private static final long DEFAULT_TTL_DAYS = 7;

    public RedisChatMemory(RedisConnectionFactory connectionFactory) {
        this.redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + conversationId;
        List<String> jsonMessages = messages.stream()
                .map(this::serialize)
                .collect(Collectors.toList());
        
        if (!jsonMessages.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, jsonMessages);
            // 设置过期时间（从配置获取，默认7天）
            // 每次有新消息都刷新过期时间，确保活跃会话不被删除
            int days = com.bluelink.util.AppConfig.getAiHistoryRetentionDays();
            redisTemplate.expire(key, java.time.Duration.ofDays(days));
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = KEY_PREFIX + conversationId;
        // lastN: Retrieve the last N messages.
        // If lastN <= 0, usually implies all messages or default limit.
        // Here we assume lastN > 0 means last N messages.
        long start = (lastN > 0) ? -lastN : 0;
        List<String> jsonList = redisTemplate.opsForList().range(key, start, -1);

        if (jsonList == null) {
            return new ArrayList<>();
        }

        return jsonList.stream()
                .map(this::deserialize)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
    }

    private String serialize(Message message) {
        try {
            MemoryMessageDto dto = new MemoryMessageDto();
            dto.setType(message.getMessageType().getValue());
            dto.setContent(message.getText());
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }

    private Message deserialize(String json) {
        try {
            MemoryMessageDto dto = objectMapper.readValue(json, MemoryMessageDto.class);
            String type = dto.getType();
            
            // Basic mapping for common message types
            if ("USER".equalsIgnoreCase(type)) {
                return new UserMessage(dto.getContent());
            } else if ("ASSISTANT".equalsIgnoreCase(type)) {
                return new AssistantMessage(dto.getContent());
            } else if ("SYSTEM".equalsIgnoreCase(type)) {
                return new SystemMessage(dto.getContent());
            } else {
                // Fallback for unknown types (e.g. TOOL) to UserMessage for safety, or just text
                return new UserMessage(dto.getContent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    public static class MemoryMessageDto {
        private String type;
        private String content;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
