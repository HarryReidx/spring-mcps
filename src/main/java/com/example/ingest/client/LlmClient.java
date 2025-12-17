package com.example.ingest.client;

import com.example.ingest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM 客户端
 * 用于纯文本的 Chat Completion 请求（章节摘要生成）
 * 支持 OpenAI、Ollama 等提供商
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {
    
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    
    private enum LlmProvider {
        OPENAI,
        OLLAMA
    }
    
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 同步生成文本摘要
     * 
     * @param content 待摘要的文本内容
     * @return 摘要文本，失败时返回空字符串
     */
    public String summarizeText(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        try {
            long startTime = System.currentTimeMillis();
            log.debug("开始 LLM 摘要生成，内容长度: {}", content.length());
            
            AppProperties.LlmConfig llmConfig = appProperties.getLlm();
            LlmProvider provider = detectProvider(llmConfig.getBaseUrl(), llmConfig.getProvider());
            
            // 构建请求体
            String requestBody = buildChatRequest(content, llmConfig.getPrompt(), provider);
            
            // 构建请求
            Request.Builder requestBuilder = new Request.Builder()
                    .url(llmConfig.getBaseUrl())
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json");
            
            // 添加 Authorization header（OpenAI 需要）
            if (llmConfig.getApiKey() != null && !llmConfig.getApiKey().isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + llmConfig.getApiKey());
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("LLM API 调用失败: status={}, body={}", response.code(), errorBody);
                    return "";
                }
                
                String responseBody = response.body().string();
                log.debug("LLM 响应: {}", responseBody);
                
                // 解析响应
                String summary = parseResponse(responseBody, provider);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("LLM 摘要生成完成，摘要长度={}, 耗时={}ms", summary.length(), duration);
                
                return summary;
            }
        } catch (Exception e) {
            log.error("LLM 摘要生成失败", e);
            return "";
        }
    }

    /**
     * 检测 LLM 提供商类型
     */
    private LlmProvider detectProvider(String baseUrl, String configProvider) {
        if (configProvider != null && !configProvider.isEmpty()) {
            if ("openai".equalsIgnoreCase(configProvider)) {
                return LlmProvider.OPENAI;
            }
            if ("ollama".equalsIgnoreCase(configProvider)) {
                return LlmProvider.OLLAMA;
            }
        }
        
        // 自动检测
        if (baseUrl.contains("11434") || baseUrl.contains("ollama")) {
            return LlmProvider.OLLAMA;
        }
        
        // 默认 OpenAI
        return LlmProvider.OPENAI;
    }

    /**
     * 构建 Chat Completion 请求体
     */
    private String buildChatRequest(String content, String systemPrompt, LlmProvider provider) throws IOException {
        AppProperties.LlmConfig llmConfig = appProperties.getLlm();
        
        if (provider == LlmProvider.OLLAMA) {
            // Ollama 格式
            Map<String, Object> requestMap = Map.of(
                    "model", llmConfig.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", content)
                    ),
                    "stream", false
            );
            return objectMapper.writeValueAsString(requestMap);
        } else {
            // OpenAI 格式
            Map<String, Object> requestMap = Map.of(
                    "model", llmConfig.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", content)
                    ),
                    "max_tokens", llmConfig.getMaxTokens()
            );
            return objectMapper.writeValueAsString(requestMap);
        }
    }

    /**
     * 解析响应
     */
    private String parseResponse(String responseBody, LlmProvider provider) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if (provider == LlmProvider.OLLAMA) {
            // Ollama 格式: {"message": {"content": "..."}}
            JsonNode message = root.path("message");
            if (message.isMissingNode()) {
                throw new IOException("Ollama 响应缺少 message 字段");
            }
            JsonNode content = message.path("content");
            if (content.isMissingNode()) {
                throw new IOException("Ollama 响应缺少 content 字段");
            }
            return content.asText().trim();
        } else {
            // OpenAI 格式: {"choices": [{"message": {"content": "..."}}]}
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.size() == 0) {
                throw new IOException("响应缺少 choices 字段或为空");
            }
            JsonNode firstChoice = choices.get(0);
            if (firstChoice == null) {
                throw new IOException("choices[0] 为空");
            }
            JsonNode message = firstChoice.path("message");
            if (message.isMissingNode()) {
                throw new IOException("响应缺少 message 字段");
            }
            JsonNode content = message.path("content");
            if (content.isMissingNode()) {
                throw new IOException("响应缺少 content 字段");
            }
            return content.asText().trim();
        }
    }
}
