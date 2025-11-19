package com.example.difyingest.client;

import com.example.difyingest.config.AppProperties;
import com.example.difyingest.model.MineruParseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MinerU 客户端，调用自建 MinerU 开源版本解析 PDF
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MineruClient {

    private final WebClient webClient;
    private final AppProperties appProperties;

    /**
     * 调用 MinerU 解析 PDF 文件
     * 
     * @param pdfBytes PDF 文件字节数组
     * @param fileName 文件名
     * @return MinerU 解析响应
     */
    public MineruParseResponse parsePdf(byte[] pdfBytes, String fileName) {
        String baseUrl = appProperties.getMineru().getBaseUrl();
        log.info("调用 MinerU 解析 PDF: {}, 大小: {} bytes, baseUrl: {}", fileName, pdfBytes.length, baseUrl);

        // MinerU API 路径
        String url = baseUrl + "/file_parse";

        // 创建 Resource 并设置正确的 Content-Type
        Resource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        // 使用 LinkedMultiValueMap 构建 multipart 请求
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        // 创建 HttpEntity 包装 Resource，明确设置 Content-Type
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        HttpEntity<Resource> fileEntity = new HttpEntity<>(fileResource, fileHeaders);
        
        body.add("files", fileEntity);

        try {
            log.debug("发送请求到 MinerU: {}, 文件名: {}, Content-Type: application/pdf", url, fileName);
            
            MineruParseResponse response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> {
                            log.error("MinerU 返回错误状态: {}", clientResponse.statusCode());
                            return clientResponse.bodyToMono(String.class)
                                .doOnNext(errorBody -> log.error("MinerU 错误响应体: {}", errorBody))
                                .flatMap(errorBody -> clientResponse.createException());
                        }
                    )
                    .bodyToMono(MineruParseResponse.class)
                    .block();

            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                log.info("MinerU 解析成功，版本: {}, 文档数量: {}", 
                        response.getVersion(), response.getResults().size());
            } else {
                log.warn("MinerU 返回空结果");
            }
            
            return response;
        } catch (Exception e) {
            log.error("调用 MinerU 失败，请检查：1) MinerU 服务是否运行 2) 接口路径是否正确 3) 参数格式是否匹配", e);
            throw new RuntimeException("MinerU 解析失败: " + e.getMessage(), e);
        }
    }
}
