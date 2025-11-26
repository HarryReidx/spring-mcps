package com.example.ingest.controller;

import com.example.ingest.model.IngestRequest;
import com.example.ingest.repository.IngestTaskLogRepository;
import com.example.ingest.repository.IngestTaskRepository;
import com.example.ingest.service.IngestTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dify/document")
@RequiredArgsConstructor
public class DocumentIngestController {
    
    private final IngestTaskService ingestTaskService;
    private final IngestTaskRepository taskRepository;
    private final IngestTaskLogRepository taskLogRepository;

    @PostMapping("/ingest/async")
    public ResponseEntity<Map<String, Object>> ingestDocumentAsync(@RequestBody IngestRequest request) {
        log.info("收到异步文档入库请求: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        UUID taskId = ingestTaskService.createAndExecuteTask(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId.toString());
        response.put("status", "PENDING");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ingest/sync")
    public ResponseEntity<Map<String, Object>> ingestDocumentSync(@RequestBody IngestRequest request) {
        log.info("收到同步文档入库请求: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        Map<String, Object> response = ingestTaskService.createAndExecuteTaskSync(request);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocument(@RequestBody IngestRequest request) {
        return ingestDocumentAsync(request);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        return taskRepository.findById(UUID.fromString(taskId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/task/{taskId}/logs")
    public ResponseEntity<?> getTaskLogs(@PathVariable String taskId) {
        return ResponseEntity.ok(taskLogRepository.findByTaskIdOrderByCreatedAtDesc(UUID.fromString(taskId)));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
