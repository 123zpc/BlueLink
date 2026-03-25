package com.bluelink.ai;

import com.bluelink.util.AppConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class SpringAiService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiService.class);

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private com.bluelink.config.EmbeddedRedisConfig embeddedRedisConfig;

    private final ChatMemory inMemoryChatMemory = new InMemoryChatMemory();
    private volatile ChatMemory redisChatMemory;
    private final Map<String, String> conversationTitles = Collections.synchronizedMap(new LinkedHashMap<>());

    private static final String CONVERSATION_TITLES_KEY = "chat:conversations:titles";

    // In-memory storage for single-turn (transient) sessions
    private final Map<String, List<Message>> localChatHistory = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> localConversationTitles = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isLocalSession(String conversationId) {
        if (conversationId == null) return false;
        return localChatHistory.containsKey(conversationId);
    }

    private ChatMemory getChatMemory() {
        if (AppConfig.isEmbeddedRedisEnabled()) {
            if (redisConnectionFactory != null) {
                if (redisChatMemory == null) {
                    synchronized (this) {
                        if (redisChatMemory == null) {
                            try {
                                redisChatMemory = new RedisChatMemory(redisConnectionFactory);
                            } catch (Exception e) {
                                log.error("Failed to initialize RedisChatMemory: {}", e.getMessage());
                                return inMemoryChatMemory;
                            }
                        }
                    }
                }
                return redisChatMemory;
            }
        }
        return inMemoryChatMemory;
    }

    public Flux<String> streamChat(String message) {
        return streamChat(message, "default");
    }

    public Flux<String> streamChat(String message, String conversationId) {
        return streamChat(message, conversationId, null);
    }

    public Flux<String> streamChat(String message, String conversationId, String modelOverride) {
        String apiKey = AppConfig.getAiApiKey();
        String rawUrl = AppConfig.getAiApiUrl();

        if (apiKey == null || apiKey.isEmpty()) {
            return Flux.just("Error: API Key is missing. Please configure it in Settings.");
        }

// import org.springframework.web.reactive.function.client.WebClientResponseException; // moved to top imports

        // 智能处理 Base URL
        String baseUrl = rawUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        
        // 移除末尾斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 默认路径
        String chatPath = "/v1/chat/completions";
        String embPath = "/v1/embeddings";

        // 如果用户配置的是完整路径 (例如包含 /chat/completions)，则尝试提取 Base URL
        // 常见情况: https://api.deepseek.com/v1/chat/completions -> https://api.deepseek.com
        if (baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
            // 如果去掉后还包含 /v1，根据情况决定是否保留。OpenAiApi 通常需要 BaseURL 不含 /v1
             if (baseUrl.endsWith("/v1")) {
                 // OpenAiApi 会拼接 chatPath (/v1/chat/completions)，所以 BaseURL 应该是 https://api.deepseek.com
                 baseUrl = baseUrl.substring(0, baseUrl.length() - "/v1".length());
             }
        } else if (baseUrl.endsWith("/v1")) {
             // 如果用户只配到了 /v1
             baseUrl = baseUrl.substring(0, baseUrl.length() - "/v1".length());
        }

        // 智谱 AI (v4) 或其他显式指定版本的 URL 检测
        if (baseUrl.matches(".*\\/v\\d+$")) {
            // 如果 BaseURL 本身带版本号且未被上述逻辑处理 (例如自定义 API)，则调整 path
            chatPath = "/chat/completions";
            embPath = "/embeddings";
        }

        try {
            // 使用 OpenAiApi 构造函数 (适配 Spring AI 1.0.0-M6)
            ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();
            OpenAiApi api = new OpenAiApi(baseUrl, apiKey, chatPath, embPath, RestClient.builder(), WebClient.builder(), errorHandler);
            
            // 使用配置的模型 (优先使用 override)
            String modelName = modelOverride;
            if (modelName == null || modelName.isEmpty()) {
                modelName = AppConfig.getAiModel();
            }
            if (modelName == null || modelName.isEmpty()) {
                modelName = "deepseek-chat";
            }
            
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(0.7)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();

            // Determine routing: Redis (Persistent) vs Local (Transient)
            // Use Redis if: Multi-turn is ENABLED AND it's NOT an existing local session
            // Note: If it's a NEW session, and Multi-turn is ENABLED, it goes to Redis.
            // If it's a NEW session, and Multi-turn is DISABLED, it goes to Local.
            boolean isLocalSession = localChatHistory.containsKey(conversationId);
            boolean isMultiTurn = AppConfig.isAiMultiTurnEnabled();
            boolean useRedis = isMultiTurn && !isLocalSession;

            if (useRedis) {
                trackConversation(conversationId, message);
                // 使用 ChatClient 和 Advisor 实现多轮对话
                ChatClient chatClient = ChatClient.builder(chatModel)
                        .defaultAdvisors(new MessageChatMemoryAdvisor(getChatMemory()))
                        .build();

                log.debug("Sending AI request (Multi-turn Redis): message='{}', conversationId='{}'", message, conversationId);

                return chatClient.prompt()
                        .user(message)
                        .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                        .stream()
                        .content()
                        .onErrorResume(e -> {
                            String errorMsg = e.getMessage();
                            if (e instanceof WebClientResponseException) {
                                WebClientResponseException we = (WebClientResponseException) e;
                                String responseBody = we.getResponseBodyAsString();
                                log.error("AI Request Failed: {} \nResponse Body: {}", errorMsg, responseBody);
                                errorMsg += "\n[Details: " + responseBody + "]";
                            } else {
                                log.error("AI Request Failed: {}", errorMsg, e);
                            }
                            return Flux.just("\n[Error: " + errorMsg + "]");
                        });
            } else {
                // Local Session (Transient) OR Single-turn Mode
                // If Multi-turn is ON (and it's a local session), we MUST include history to simulate multi-turn.
                // If Multi-turn is OFF, we do NOT include history (Strict Single-turn logic).
                
                log.debug("Sending AI request (Local/Transient): message='{}', MultiTurn={}", message, isMultiTurn);
                
                // Track conversation title locally
                trackConversation(conversationId, message);
                
                // Prepare Context
                List<Message> promptMessages = new java.util.ArrayList<>();
                if (isMultiTurn && isLocalSession) { 
                    // Even if local, if MultiTurn is ON, we send history
                    List<Message> history = localChatHistory.get(conversationId);
                    if (history != null) promptMessages.addAll(history);
                }
                promptMessages.add(new UserMessage(message));
                
                // Track user message locally
                if (conversationId != null) {
                    localChatHistory.computeIfAbsent(conversationId, k -> new java.util.ArrayList<>())
                        .add(new UserMessage(message));
                }
                
                // Use a StringBuilder to accumulate response for local storage
                StringBuilder fullResponse = new StringBuilder();
                
                return chatModel.stream(new Prompt(promptMessages))
                        .map(response -> {
                            if (response.getResult() != null && response.getResult().getOutput() != null) {
                                String text = response.getResult().getOutput().getText();
                                if (text != null) {
                                    fullResponse.append(text);
                                    return text;
                                }
                            }
                            return "";
                        })
                        .doOnComplete(() -> {
                            // Track assistant message locally after stream completes
                            if (conversationId != null && fullResponse.length() > 0) {
                                localChatHistory.computeIfAbsent(conversationId, k -> new java.util.ArrayList<>())
                                    .add(new org.springframework.ai.chat.messages.AssistantMessage(fullResponse.toString()));
                            }
                        })
                        .onErrorResume(e -> {
                            String errorMsg = e.getMessage();
                            if (e instanceof WebClientResponseException) {
                                WebClientResponseException we = (WebClientResponseException) e;
                                String responseBody = we.getResponseBodyAsString();
                                log.error("AI Request Failed (Single-turn): {} \nResponse Body: {}", errorMsg, responseBody);
                                errorMsg += "\n[Details: " + responseBody + "]";
                            } else {
                                log.error("AI Request Failed (Single-turn): {}", errorMsg, e);
                            }
                            return Flux.just("\n[Error: " + errorMsg + "]");
                        });
            }

        } catch (Exception e) {
            log.error("Error initializing AI client: {}", e.getMessage(), e);
            return Flux.just("Error initializing AI client: " + e.getMessage());
        }
    }

    public List<Message> getHistory(String conversationId) {
        // Check local first
        if (localChatHistory.containsKey(conversationId)) {
            return localChatHistory.get(conversationId);
        }
        
        if (!AppConfig.isAiMultiTurnEnabled()) {
            return Collections.emptyList();
        }
        if (conversationId == null || conversationId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return getChatMemory().get(conversationId, 0);
        } catch (Exception e) {
            log.error("Failed to load history for conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    public Map<String, String> getConversations() {
        Map<String, String> result = new LinkedHashMap<>();
        
        // 1. Load Redis conversations if Multi-turn is enabled
        if (AppConfig.isAiMultiTurnEnabled()) {
            if (conversationTitles.isEmpty() && AppConfig.isEmbeddedRedisEnabled() && redisConnectionFactory != null) {
                loadConversationsFromRedis();
            }
            synchronized (conversationTitles) {
                result.putAll(conversationTitles);
            }
        }
        
        // 2. Load Local conversations (Always available, labeled as Temporary if mixed)
        // If Multi-turn is enabled, we append " (临时会话)" to distinguish
        // If Multi-turn is disabled, we show them as is (since they are the only ones)
        boolean appendLabel = AppConfig.isAiMultiTurnEnabled();
        
        localConversationTitles.forEach((id, title) -> {
            if (appendLabel) {
                result.put(id, title + " (临时会话)");
            } else {
                result.put(id, title);
            }
        });
        
        return result;
    }

    /**
     * Get title for a specific conversation ID, checking both local and Redis storage
     * regardless of the current multi-turn mode.
     */
    public String getConversationTitle(String conversationId) {
        if (conversationId == null) return null;
        
        // Check local first
        if (localConversationTitles.containsKey(conversationId)) {
            String title = localConversationTitles.get(conversationId);
            // Append label if needed (consistent with getConversations)
            if (AppConfig.isAiMultiTurnEnabled()) {
                return title + " (临时会话)";
            }
            return title;
        }
        
        // Check Redis (memory cache)
        if (conversationTitles.containsKey(conversationId)) {
            return conversationTitles.get(conversationId);
        }
        
        // Check Redis (Force load if needed? No, avoid heavy op for single title check if possible, 
        // but maybe we should if it's critical. For now, assume cache is populated or we accept miss)
        
        return null;
    }
    
    public void recordConversationTitle(String conversationId, String message) {
        trackConversation(conversationId, message);
    }
    
    public void appendLocalUserMessage(String conversationId, String message) {
        if (conversationId == null || message == null) {
            return;
        }
        localChatHistory.computeIfAbsent(conversationId, k -> new java.util.ArrayList<>())
                .add(new UserMessage(message));
    }
    
    public void appendLocalAssistantMessage(String conversationId, String message) {
        if (conversationId == null || message == null || message.isEmpty()) {
            return;
        }
        localChatHistory.computeIfAbsent(conversationId, k -> new java.util.ArrayList<>())
                .add(new AssistantMessage(message));
    }
    
    public String buildRemotePrompt(String conversationId, String message) {
        boolean isMultiTurn = AppConfig.isAiMultiTurnEnabled();
        if (!isMultiTurn || conversationId == null) {
            return message;
        }
        List<Message> history = localChatHistory.get(conversationId);
        if (history == null || history.isEmpty()) {
            return message;
        }
        List<Message> promptMessages = new java.util.ArrayList<>(history);
        promptMessages.add(new UserMessage(message));
        return renderPromptText(promptMessages);
    }
    
    private String renderPromptText(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message m : messages) {
            if (m == null) {
                continue;
            }
            String text = m.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            String type = m.getMessageType() != null ? m.getMessageType().getValue() : "message";
            String label;
            if ("user".equalsIgnoreCase(type)) {
                label = "User";
            } else if ("assistant".equalsIgnoreCase(type)) {
                label = "Assistant";
            } else if ("system".equalsIgnoreCase(type)) {
                label = "System";
            } else {
                label = "Message";
            }
            builder.append(label).append(": ").append(text).append("\n");
        }
        return builder.toString().trim();
    }

    private void loadConversationsFromRedis() {
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = new org.springframework.data.redis.core.StringRedisTemplate(redisConnectionFactory);
            Map<Object, Object> entries = template.opsForHash().entries(CONVERSATION_TITLES_KEY);
            
            // 懒惰删除：检查对应的聊天记录是否存在，如果不存在则认为是已过期，清理标题
            // 注意：这会增加启动时的 Redis 请求量，但对于单用户桌面应用来说是可以接受的
            List<Object> keysToDelete = new java.util.ArrayList<>();
            
            synchronized (conversationTitles) {
                entries.forEach((k, v) -> {
                    String cid = (String) k;
                    // 检查聊天记录 Key 是否存在
                    Boolean hasHistory = template.hasKey("chat:memory:" + cid);
                    if (Boolean.TRUE.equals(hasHistory)) {
                        conversationTitles.put(cid, (String) v);
                    } else {
                        keysToDelete.add(k);
                    }
                });
            }
            
            // 批量清理过期的标题
            if (!keysToDelete.isEmpty()) {
                template.opsForHash().delete(CONVERSATION_TITLES_KEY, keysToDelete.toArray());
                log.info("Cleaned up {} expired conversation titles", keysToDelete.size());
            }
            
            log.info("Loaded {} active conversations from Redis", conversationTitles.size());
        } catch (Exception e) {
            log.error("Failed to load conversations from Redis", e);
        }
    }

    private void trackConversation(String conversationId, String message) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }
        
        // 如果多轮对话被禁用，使用本地内存记录
        if (!AppConfig.isAiMultiTurnEnabled()) {
            if (!localConversationTitles.containsKey(conversationId)) {
                localConversationTitles.put(conversationId, buildTitle(message));
            }
            return;
        }
        
        // 如果已经有了，就不更新标题了（保留第一次的标题）
        // 但我们需要更新 Redis 的 TTL
        boolean exists = conversationTitles.containsKey(conversationId);
        
        // Refresh TTL if using Redis (Always try to refresh expiration)
        if (AppConfig.isEmbeddedRedisEnabled() && redisConnectionFactory != null) {
             try {
                 org.springframework.data.redis.core.StringRedisTemplate template = new org.springframework.data.redis.core.StringRedisTemplate(redisConnectionFactory);
                 // 刷新聊天记录的 TTL (从配置获取)
                 // 注意：RedisChatMemory.add() 已经做了这件事，但如果是读取或者其他操作，这里是一个补充
                 int days = AppConfig.getAiHistoryRetentionDays();
                 template.expire("chat:memory:" + conversationId, java.time.Duration.ofDays(days));
             } catch (Exception e) {
                 // ignore
             }
        }
        
        if (exists) {
            return;
        }
        
        String title = buildTitle(message);
        conversationTitles.put(conversationId, title);
        
        // Persist to Redis
        if (AppConfig.isEmbeddedRedisEnabled() && redisConnectionFactory != null) {
            try {
                org.springframework.data.redis.core.StringRedisTemplate template = new org.springframework.data.redis.core.StringRedisTemplate(redisConnectionFactory);
                template.opsForHash().put(CONVERSATION_TITLES_KEY, conversationId, title);
            } catch (Exception e) {
                log.error("Failed to save conversation title to Redis", e);
            }
        }
    }

    private String buildTitle(String message) {
        String title = message == null ? "" : message.trim();
        if (title.isEmpty()) {
            return "New Chat";
        }
        if (title.length() > 30) {
            return title.substring(0, 30) + "...";
        }
        return title;
    }
    
    public void startRedis() {
        if (embeddedRedisConfig != null) {
            embeddedRedisConfig.startRedis();
        }
    }
    
    public void stopRedis() {
        if (embeddedRedisConfig != null) {
            embeddedRedisConfig.stopRedis();
        }
    }
}
