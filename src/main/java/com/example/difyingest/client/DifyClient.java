package com.example.difyingest.client;

import com.example.difyingest.config.AppProperties;
import com.example.difyingest.model.DifyCreateDocumentRequest;
import com.example.difyingest.model.DifyCreateDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Dify 客户端，调用 Dify 知识库 API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyClient {

    private final WebClient webClient;
    private final AppProperties appProperties;

    /**
     * 调用 Dify 知识库接口，通过文本创建文档
     * 
     * Dify API 路径可能是以下之一：
     * - /v1/datasets/{dataset_id}/document/create_by_text
     * - /v1/datasets/{dataset_id}/documents
     * - /console/api/datasets/{dataset_id}/documents
     * 
     * @param datasetId 知识库 ID
     * @param request 创建文档请求
     * @return Dify 响应
     */
    public DifyCreateDocumentResponse createDocumentByText(String datasetId, DifyCreateDocumentRequest request) {
        String baseUrl = appProperties.getDify().getBaseUrl();
        String apiKey = appProperties.getDify().getApiKey();
        
        // Dify 官方 API 路径使用下划线而不是连字符
        String url = baseUrl + "/v1/datasets/" + datasetId + "/document/create_by_text";

        log.info("调用 Dify 创建文档: datasetId={}, fileName={}, textLength={}", 
                datasetId, request.getName(), request.getText() != null ? request.getText().length() : 0);
        log.debug("Dify URL: {}", url);

        try {
            DifyCreateDocumentResponse response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> {
                            log.error("Dify 返回错误状态: {}", clientResponse.statusCode());
                            return clientResponse.bodyToMono(String.class)
                                .doOnNext(errorBody -> log.error("Dify 错误响应体: {}", errorBody))
                                .flatMap(errorBody -> clientResponse.createException());
                        }
                    )
                    .bodyToMono(DifyCreateDocumentResponse.class)
                    .block();

            log.info("Dify 创建文档成功: documentId={}", 
                    response != null && response.getDocument() != null ? response.getDocument().getId() : null);
            return response;
        } catch (Exception e) {
            log.error("调用 Dify 失败: {}，请检查：1) Dify 服务是否正常 2) API Key 是否正确 3) datasetId 是否存在", e.getMessage());
            throw new RuntimeException("Dify 创建文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback 方法：直接通过文件创建文档（不走 MinerU）
     * TODO: 预留方法，根据需要实现
     * 
     * @param datasetId 知识库 ID
     * @param fileBytes 文件字节数组
     * @param fileName 文件名
     * @return Dify 响应
     */
    public DifyCreateDocumentResponse createDocumentByFile(String datasetId, byte[] fileBytes, String fileName) {
        // TODO: 实现直接上传文件到 Dify 的逻辑
        log.warn("createDocumentByFile 方法尚未实现");
        throw new UnsupportedOperationException("createDocumentByFile 方法尚未实现");
    }
}
