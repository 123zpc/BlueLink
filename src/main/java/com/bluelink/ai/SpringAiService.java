package com.bluelink.ai;

import com.bluelink.util.AppConfig;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class SpringAiService {

    public Flux<String> streamChat(String message) {
        String apiKey = AppConfig.getAiApiKey();
        String rawUrl = AppConfig.getAiApiUrl();

        if (apiKey == null || apiKey.isEmpty()) {
            return Flux.just("Error: API Key is missing. Please configure it in Settings.");
        }

        // 智能处理 Base URL
        String baseUrl = rawUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 默认路径
        String chatPath = "/v1/chat/completions";
        String embPath = "/v1/embeddings";

        // 智谱 AI (v4) 或其他显式指定版本的 URL 检测
        // 如果 baseUrl 已经以 /v4 或 /v1 结尾，去掉 path 里的 /v1
        if (baseUrl.matches(".*\\/v\\d+$")) {
            chatPath = "/chat/completions";
            embPath = "/embeddings";
        }

        try {
            // 使用 OpenAiApi 构造函数 (适配 Spring AI 1.0.0-M6)
            ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();
            OpenAiApi api = new OpenAiApi(baseUrl, apiKey, chatPath, embPath, RestClient.builder(), WebClient.builder(), errorHandler);
            
            // 使用配置的模型
            String modelName = AppConfig.getAiModel();
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

            return chatModel.stream(new Prompt(new UserMessage(message)))
                    .map(response -> {
                        if (response.getResult() != null && response.getResult().getOutput() != null) {
                            String text = response.getResult().getOutput().getText();
                            return text != null ? text : "";
                        }
                        return "";
                    })
                    .onErrorResume(e -> Flux.just("\n[Error: " + e.getMessage() + "]"));
        } catch (Exception e) {
            e.printStackTrace();
            return Flux.just("Error initializing AI client: " + e.getMessage());
        }
    }
}
