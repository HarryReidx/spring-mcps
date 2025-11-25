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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * VLM (Vision Language Model) 客户端
 * 用于图片语义理解和 OCR 提取
 * 支持 OpenAI 和 Ollama 两种模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VlmClient {
    
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    
    private enum VlmProvider {
        OPENAI,
        OLLAMA
    }
    
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 异步分析图片（支持 GPT-4o / Claude-3.5 等视觉模型）
     * 
     * @param imageUrl 图片 URL
     * @param imageName 图片名称（用于日志）
     * @return CompletableFuture<ImageAnalysisResult>
     */
    public CompletableFuture<ImageAnalysisResult> analyzeImageAsync(String imageUrl, String imageName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return analyzeImage(imageUrl, imageName);
            } catch (Exception e) {
                log.error("VLM 分析图片失败: {}", imageName, e);
                return ImageAnalysisResult.builder()
                        .imageName(imageName)
                        .description("图片分析失败")
                        .ocrText("")
                        .success(false)
                        .build();
            }
        });
    }

    /**
     * 同步分析图片
     */
    private ImageAnalysisResult analyzeImage(String imageUrl, String imageName) throws IOException {
        log.info("开始 VLM 分析图片: {}", imageName);
        
        AppProperties.VlmConfig vlmConfig = appProperties.getVlm();
        VlmProvider provider = detectProvider(vlmConfig.getBaseUrl());
        
        log.debug("检测到 VLM 提供商: {}", provider);
        
        // Ollama 需要下载图片并转换为 base64
        String imageData = imageUrl;
        if (provider == VlmProvider.OLLAMA) {
            imageData = downloadAndEncodeImage(imageUrl);
            log.debug("图片已转换为 base64，长度: {}", imageData.length());
        }
        
        // 构建请求体（根据提供商类型）
        String requestBody = buildVisionRequest(imageData, vlmConfig.getPrompt(), provider);
        
        // 构建请求（Ollama 不需要 Authorization header）
        Request.Builder requestBuilder = new Request.Builder()
                .url(vlmConfig.getBaseUrl())
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json");
        
        // OpenAI 需要 Authorization header
        if (provider == VlmProvider.OPENAI && vlmConfig.getApiKey() != null && !vlmConfig.getApiKey().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + vlmConfig.getApiKey());
        }
        
        Request request = requestBuilder.build();
        
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("VLM API 调用失败: status={}, body={}", response.code(), errorBody);
                throw new IOException("VLM API 调用失败: " + errorBody);
            }
            
            String responseBody = response.body().string();
            log.debug("VLM 响应: {}", responseBody);
            
            // 解析响应（根据提供商类型）
            String content = parseResponse(responseBody, provider);
            
            // 简单解析：假设返回格式为 "描述: xxx\nOCR: yyy"
            String description = content;
            String ocrText = "";
            
            if (content.contains("OCR:")) {
                String[] parts = content.split("OCR:", 2);
                description = parts[0].replace("描述:", "").trim();
                ocrText = parts[1].trim();
            }
            
            log.info("VLM 分析完成: {} - 描述长度={}, OCR长度={}", 
                    imageName, description.length(), ocrText.length());
            
            return ImageAnalysisResult.builder()
                    .imageName(imageName)
                    .description(description)
                    .ocrText(ocrText)
                    .success(true)
                    .build();
        }
    }

    /**
     * 下载图片并转换为 base64
     */
    private String downloadAndEncodeImage(String imageUrl) throws IOException {
        log.debug("下载图片: {}", imageUrl);
        
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();
        
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载图片失败: " + response.code());
            }
            
            byte[] imageBytes = response.body().bytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            
            log.debug("图片下载完成，大小: {} bytes", imageBytes.length);
            return base64;
        }
    }

    /**
     * 检测 VLM 提供商类型
     */
    private VlmProvider detectProvider(String baseUrl) {
        if (baseUrl.contains("11434") || baseUrl.contains("ollama")) {
            return VlmProvider.OLLAMA;
        }
        return VlmProvider.OPENAI;
    }

    /**
     * 解析响应（根据提供商类型）
     */
    private String parseResponse(String responseBody, VlmProvider provider) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if (provider == VlmProvider.OLLAMA) {
            // Ollama 响应格式: {"message": {"content": "..."}}
            return root.path("message").path("content").asText();
        } else {
            // OpenAI 响应格式: {"choices": [{"message": {"content": "..."}}]}
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }

    /**
     * 构建 Vision API 请求体（根据提供商类型）
     * 
     * @param imageData 对于 Ollama 是 base64 字符串，对于 OpenAI 是 URL
     */
    private String buildVisionRequest(String imageData, String prompt, VlmProvider provider) throws IOException {
        Map<String, Object> requestMap;
        
        if (provider == VlmProvider.OLLAMA) {
            // Ollama 格式: /api/chat
            // images 字段需要 base64 编码的图片数据
            requestMap = Map.of(
                    "model", appProperties.getVlm().getModel(),
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt,
                                    "images", List.of(imageData)  // base64 字符串
                            )
                    ),
                    "stream", false
            );
        } else {
            // OpenAI 格式: /v1/chat/completions
            // image_url 字段使用 URL
            requestMap = Map.of(
                    "model", appProperties.getVlm().getModel(),
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "text", "text", prompt),
                                            Map.of("type", "image_url", "image_url", Map.of("url", imageData))
                                    )
                            )
                    ),
                    "max_tokens", appProperties.getVlm().getMaxTokens()
            );
        }
        
        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * 图片分析结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ImageAnalysisResult {
        private String imageName;
        private String description;
        private String ocrText;
        private boolean success;
    }
}
