package com.example.ingest.client;

import com.example.ingest.config.AppProperties;
import com.example.ingest.exception.DifyException;
import com.example.ingest.model.DifyCreateDocumentRequest;
import com.example.ingest.model.DifyCreateDocumentResponse;
import com.example.ingest.model.DifyDatasetDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Dify API 客户端
 * 负责与 Dify 平台交互
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyClient {
    
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取 Dataset 详情
     * 
     * @param datasetId Dataset ID
     * @return Dataset 详情
     */
    public DifyDatasetDetail getDatasetDetail(String datasetId) {
        log.info("获取 Dataset 详情: datasetId={}", datasetId);
        
        String url = String.format("%s/datasets/%s", 
                appProperties.getDify().getBaseUrl(), datasetId);
        
        try {
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + appProperties.getDify().getApiKey())
                    .build();
            
            try (Response response = getHttpClient().newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("获取 Dataset 详情失败: status={}, body={}", response.code(), errorBody);
                    throw new DifyException("获取 Dataset 详情失败: " + errorBody);
                }
                
                String responseBody = response.body().string();
                log.debug("Dataset 详情响应: {}", responseBody);
                
                return objectMapper.readValue(responseBody, DifyDatasetDetail.class);
            }
            
        } catch (IOException e) {
            log.error("获取 Dataset 详情失败", e);
            throw new DifyException("获取 Dataset 详情失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 Dify API 创建文档（写入知识库）
     * 
     * @param datasetId Dataset ID
     * @param request 创建文档请求
     * @return 创建结果
     */
    public DifyCreateDocumentResponse createDocument(String datasetId, DifyCreateDocumentRequest request) {
        log.info("开始调用 Dify API 创建文档: datasetId={}, name={}", datasetId, request.getName());
        
        String url = String.format("%s/datasets/%s/document/create_by_text", 
                appProperties.getDify().getBaseUrl(), datasetId);
        
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            log.debug("Dify 请求体: {}", requestBody);
            
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + appProperties.getDify().getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = getHttpClient().newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Dify API 调用失败: status={}, body={}", response.code(), errorBody);
                    throw new DifyException("Dify API 调用失败: " + errorBody);
                }
                
                String responseBody = response.body().string();
                log.debug("Dify 响应: {}", responseBody);
                
                DifyCreateDocumentResponse difyResponse = objectMapper.readValue(responseBody, DifyCreateDocumentResponse.class);
                log.info("Dify 文档创建成功: documentId={}", difyResponse.getDocument().getId());
                
                return difyResponse;
            }
            
        } catch (IOException e) {
            log.error("调用 Dify API 失败", e);
            throw new DifyException("调用 Dify API 失败: " + e.getMessage(), e);
        }
    }
}
