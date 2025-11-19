package com.example.difyingest.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MinerU API 测试工具
 * 用于测试不同的接口路径和参数组合
 */
@Slf4j
@Component
public class MineruApiTester {

    private final WebClient webClient;

    public MineruApiTester(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 测试 MinerU 接口
     * 
     * @param baseUrl MinerU 基础 URL
     * @param path 接口路径
     * @param paramName 文件参数名
     * @param pdfBytes PDF 文件字节
     * @param fileName 文件名
     */
    public void testMineruApi(String baseUrl, String path, String paramName, byte[] pdfBytes, String fileName) {
        String url = baseUrl + path;
        log.info("测试 MinerU API: {}, 参数名: {}, 文件: {}", url, paramName, fileName);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(paramName, new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(MediaType.APPLICATION_PDF);

        try {
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("✓ 成功! 响应: {}", response);
        } catch (Exception e) {
            log.error("✗ 失败: {}", e.getMessage());
        }
    }

    /**
     * 批量测试常见的 MinerU 接口配置
     */
    public void testCommonConfigurations(String baseUrl, byte[] pdfBytes, String fileName) {
        log.info("开始批量测试 MinerU 接口配置...");
        
        // 常见的路径和参数名组合
        String[][] configs = {
            {"/v1/pdf/parse", "pdf_file"},
            {"/v1/pdf/parse", "file"},
            {"/api/parse", "pdf_file"},
            {"/api/parse", "file"},
            {"/parse", "pdf_file"},
            {"/parse", "file"},
            {"/file_parse", "pdf_file"},
            {"/file_parse", "file"},
            {"/upload", "file"},
            {"/pdf/upload", "file"}
        };

        for (String[] config : configs) {
            testMineruApi(baseUrl, config[0], config[1], pdfBytes, fileName);
            try {
                Thread.sleep(500); // 避免请求过快
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("批量测试完成");
    }
}
