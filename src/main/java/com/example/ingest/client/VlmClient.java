package com.example.ingest.client;

import com.example.ingest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * VLM (Vision Language Model) 客户端
 * 用于图片语义理解和 OCR 提取
 * 支持 OpenAI、Qwen、ModelVerse 等多种提供商
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VlmClient {
    
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    
    @Qualifier("vlmExecutor")
    private final Executor vlmExecutor;
    
    private enum VlmProvider {
        OPENAI,
        QWEN,
        MODELVERSE,
        OLLAMA
    }
    
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)  // 10 分钟，防止 GPU 排队导致超时
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 异步分析图片（支持 GPT-4o / Claude-3.5 等视觉模型）
     * 使用自定义线程池，避免阻塞 ForkJoinPool.commonPool()
     * 
     * @param imageUrl 图片 URL
     * @param imageName 图片名称（用于日志）
     * @param context 图片周围的上下文文本（前后各20字符）
     * @return CompletableFuture<ImageAnalysisResult>
     */
    public CompletableFuture<ImageAnalysisResult> analyzeImageAsync(String imageUrl, String imageName, String context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return analyzeImage(imageUrl, imageName, context);
            } catch (Exception e) {
                log.error("VLM 分析图片失败: {}", imageName, e);
                return ImageAnalysisResult.builder()
                        .imageName(imageName)
                        .description("图片分析失败")
                        .ocrText("")
                        .success(false)
                        .build();
            }
        }, vlmExecutor);  // 使用自定义线程池
    }

    /**
     * 同步分析图片
     * 
     * @param imageUrl 图片 URL
     * @param imageName 图片名称
     * @param context 图片周围的上下文文本
     * @return 分析结果
     */
    private ImageAnalysisResult analyzeImage(String imageUrl, String imageName, String context) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("开始 VLM 分析图片: {}", imageName);
        
        AppProperties.VlmConfig vlmConfig = appProperties.getVlm();
        VlmProvider provider = detectProvider(vlmConfig.getBaseUrl());
        
        log.debug("检测到 VLM 提供商: {}", provider);
        
        // Ollama 需要 base64，其他使用 URL
        String imageData;
        if (provider == VlmProvider.OLLAMA) {
            imageData = downloadAndEncodeImage(imageUrl);
        } else {
            imageData = imageUrl;
        }
        
        // 拼接上下文到 Prompt
        String configPrompt = vlmConfig.getPrompt();
        String finalPrompt = String.format("结合图片周围的上下文文本：【%s】，%s", context, configPrompt);
        log.debug("VLM理解Prompt: {}", finalPrompt);
        
        // 构建请求体
        String requestBody = buildVisionRequest(imageData, finalPrompt, provider);
        
        // 构建请求
        Request.Builder requestBuilder = new Request.Builder()
                .url(vlmConfig.getBaseUrl())
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json");
        
        // 添加 Authorization header
        if (vlmConfig.getApiKey() != null && !vlmConfig.getApiKey().isEmpty()) {
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
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("VLM 分析完成: {} - 描述长度={}, OCR长度={}, 耗时={}ms", 
                    imageName, description.length(), ocrText.length(), duration);
            
            return ImageAnalysisResult.builder()
                    .imageName(imageName)
                    .description(description)
                    .ocrText(ocrText)
                    .success(true)
                    .duration(duration)
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
        String provider = appProperties.getVlm().getProvider();
        if (provider != null && !provider.isEmpty()) {
            switch (provider.toLowerCase()) {
                case "qwen": return VlmProvider.QWEN;
                case "modelverse": return VlmProvider.MODELVERSE;
                case "openai": return VlmProvider.OPENAI;
                case "ollama": return VlmProvider.OLLAMA;
            }
        }
        // 自动检测
        if (baseUrl.contains("dashscope.aliyuncs.com")) return VlmProvider.QWEN;
        if (baseUrl.contains("modelverse.cn")) return VlmProvider.MODELVERSE;
        if (baseUrl.contains("11434") || baseUrl.contains("ollama")) return VlmProvider.OLLAMA;
        // 默认 Ollama
        return VlmProvider.OLLAMA;
    }

    /**
     * 解析响应
     */
    private String parseResponse(String responseBody, VlmProvider provider) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if (provider == VlmProvider.OLLAMA) {
            // Ollama 格式: {"message": {"content": "..."}}
            JsonNode message = root.path("message");
            if (message.isMissingNode()) {
                throw new IOException("Ollama 响应缺少 message 字段");
            }
            JsonNode content = message.path("content");
            if (content.isMissingNode()) {
                throw new IOException("Ollama 响应缺少 content 字段");
            }
            return content.asText();
        } else {
            // OpenAI 兼容格式: {"choices": [{"message": {"content": "..."}}]}
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
            return content.asText();
        }
    }

    /**
     * 构建 Vision API 请求体
     */
    private String buildVisionRequest(String imageData, String prompt, VlmProvider provider) throws IOException {
        if (provider == VlmProvider.OLLAMA) {
            // 构建 Ollama 专属的 Options 参数
            Map<String, Object> options = Map.of(
                    "temperature", 0.7,             // 适当增加随机性
                    "repeat_penalty", 1.2,          // 重复惩罚：大于 1.0 惩罚重复，建议 1.1-1.5
                    "repeat_last_n", 64,            // 检查最近的 64 个 token 是否重复
                    "top_p", 0.9,                   // 核采样
                    "num_predict", 512              // 限制最大生成长度，防止无限输出
            );
            // Ollama 格式
            Map<String, Object> requestMap = Map.of(
                    "model", appProperties.getVlm().getModel(),
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt,
                                    "images", List.of(imageData)
                            )
                    ),
                    "stream", false,
                    "options", options  // 注入惩罚参数
            );
            return objectMapper.writeValueAsString(requestMap);
        } else {
            // OpenAI 兼容格式（OpenAI, Qwen, ModelVerse）
            Map<String, Object> requestMap = Map.of(
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
            return objectMapper.writeValueAsString(requestMap);
        }
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
        private long duration;  // VLM 请求耗时（毫秒）
    }
}
