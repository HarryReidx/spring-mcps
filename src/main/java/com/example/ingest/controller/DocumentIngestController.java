package com.example.ingest.controller;

import com.example.ingest.model.IngestRequest;
import com.example.ingest.model.IngestResponse;
import com.example.ingest.service.DocumentIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 文档入库控制器
 * 对外暴露 HTTP 接口，供 Dify HTTP 插件调用
 */
@Slf4j
@RestController
@RequestMapping("/api/dify/document")
@RequiredArgsConstructor
public class DocumentIngestController {
    
    private final DocumentIngestService documentIngestService;

    /**
     * 文档入库接口
     * POST /api/dify/document/ingest
     * 
     * 请求体示例：
     * {
     *   "datasetId": "xxx",
     *   "fileUrl": "http://xxx/file.pdf",
     *   "fileName": "file.pdf",
     *   "fileType": "pdf"
     * }
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(@RequestBody IngestRequest request) {
        log.info("收到文档入库请求: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        IngestResponse response = documentIngestService.ingestDocument(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
