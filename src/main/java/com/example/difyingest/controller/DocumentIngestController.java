package com.example.difyingest.controller;

import com.example.difyingest.model.DocumentIngestRequest;
import com.example.difyingest.model.DocumentIngestResponse;
import com.example.difyingest.service.DocumentIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 文档入库 Controller
 * 对外暴露 HTTP 接口供 Dify 调用
 */
@Slf4j
@RestController
@RequestMapping("/api/dify/document")
@RequiredArgsConstructor
public class DocumentIngestController {

    private final DocumentIngestService documentIngestService;

    /**
     * 文档入库接口
     * 
     * @param request 入库请求
     * @return 入库结果
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocumentIngestResponse> ingestDocument(@RequestBody DocumentIngestRequest request) {
        log.info("收到文档入库请求: datasetId={}, fileName={}, fileType={}", 
                request.getDatasetId(), request.getFileName(), request.getFileType());
        
        DocumentIngestResponse response = documentIngestService.ingestDocument(request);
        return ResponseEntity.ok(response);
    }
}
