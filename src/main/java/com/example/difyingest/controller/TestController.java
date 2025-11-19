package com.example.difyingest.controller;

import com.example.difyingest.config.AppProperties;
import com.example.difyingest.util.MineruApiTester;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试 Controller
 * 用于测试 MinerU 接口配置
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final AppProperties appProperties;
    private final MineruApiTester mineruApiTester;
    private final WebClient webClient;

    /**
     * 测试 MinerU 连接
     * GET /api/test/mineru
     */
    @GetMapping("/mineru")
    public Map<String, Object> testMineruConnection() {
        Map<String, Object> result = new HashMap<>();
        String baseUrl = appProperties.getMineru().getBaseUrl();
        
        result.put("mineruBaseUrl", baseUrl);
        
        try {
            // 尝试访问根路径
            String response = webClient.get()
                    .uri(baseUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            result.put("status", "success");
            result.put("message", "MinerU 服务可访问");
            result.put("response", response);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "无法连接到 MinerU: " + e.getMessage());
            log.error("测试 MinerU 连接失败", e);
        }
        
        return result;
    }

    /**
     * 批量测试 MinerU 接口配置
     * POST /api/test/mineru/batch
     * 
     * 请求体：
     * {
     *   "fileUrl": "http://example.com/test.pdf"
     * }
     */
    @PostMapping("/mineru/batch")
    public Map<String, String> batchTestMinerU(@RequestBody Map<String, String> request) {
        String fileUrl = request.get("fileUrl");
        
        if (fileUrl == null || fileUrl.isEmpty()) {
            return Map.of("error", "请提供 fileUrl 参数");
        }

        try {
            // 下载测试文件
            byte[] pdfBytes = webClient.get()
                    .uri(fileUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (pdfBytes == null || pdfBytes.length == 0) {
                return Map.of("error", "下载文件失败或文件为空");
            }

            String baseUrl = appProperties.getMineru().getBaseUrl();
            mineruApiTester.testCommonConfigurations(baseUrl, pdfBytes, "test.pdf");

            return Map.of(
                "status", "completed",
                "message", "已测试多种配置，请查看日志获取详细结果"
            );
        } catch (Exception e) {
            log.error("批量测试失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 测试单个 MinerU 接口配置
     * POST /api/test/mineru/single
     * 
     * 请求体：
     * {
     *   "fileUrl": "http://example.com/test.pdf",
     *   "path": "/v1/pdf/parse",
     *   "paramName": "pdf_file"
     * }
     */
    @PostMapping("/mineru/single")
    public Map<String, String> singleTestMinerU(@RequestBody Map<String, String> request) {
        String fileUrl = request.get("fileUrl");
        String path = request.getOrDefault("path", "/v1/pdf/parse");
        String paramName = request.getOrDefault("paramName", "pdf_file");

        if (fileUrl == null || fileUrl.isEmpty()) {
            return Map.of("error", "请提供 fileUrl 参数");
        }

        try {
            byte[] pdfBytes = webClient.get()
                    .uri(fileUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (pdfBytes == null || pdfBytes.length == 0) {
                return Map.of("error", "下载文件失败或文件为空");
            }

            String baseUrl = appProperties.getMineru().getBaseUrl();
            mineruApiTester.testMineruApi(baseUrl, path, paramName, pdfBytes, "test.pdf");

            return Map.of(
                "status", "completed",
                "message", "测试完成，请查看日志获取详细结果",
                "testedUrl", baseUrl + path,
                "paramName", paramName
            );
        } catch (Exception e) {
            log.error("单个测试失败", e);
            return Map.of("error", e.getMessage());
        }
    }
}
