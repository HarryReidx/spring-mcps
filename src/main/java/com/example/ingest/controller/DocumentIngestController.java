package com.example.ingest.controller;

import com.example.ingest.client.DifyClient;
import com.example.ingest.model.DifyDatasetDetail;
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

/**
 * 文档入库控制器
 * 提供同步/异步文档入库接口
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@RestController
@RequestMapping("/api/dify/document")
@RequiredArgsConstructor
public class DocumentIngestController {
    
    private final IngestTaskService ingestTaskService;
    private final IngestTaskRepository taskRepository;
    private final IngestTaskLogRepository taskLogRepository;
    private final DifyClient difyClient;

    /**
     * 异步文档入库
     * 立即返回任务 ID，后台处理
     */
    @PostMapping("/ingest/async")
    public ResponseEntity<Map<String, Object>> ingestDocumentAsync(@RequestBody IngestRequest request) {
        log.info("收到异步文档入库请求: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        // 参数校验
        validateRequest(request);
        
        // 前置校验：查询 Dataset 配置
        DifyDatasetDetail dataset = validateAndGetDataset(request);
        
        // 创建并执行异步任务
        UUID taskId = ingestTaskService.createAndExecuteTask(request, dataset);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId.toString());
        response.put("status", "PENDING");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 同步文档入库
     * 阻塞等待，返回完整结果
     */
    @PostMapping("/ingest/sync")
    public ResponseEntity<Map<String, Object>> ingestDocumentSync(@RequestBody IngestRequest request) {
        log.info("收到同步文档入库请求: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        // 参数校验
        validateRequest(request);
        
        // 前置校验：查询 Dataset 配置
        DifyDatasetDetail dataset = validateAndGetDataset(request);
        
        // 创建并执行同步任务
        Map<String, Object> response = ingestTaskService.createAndExecuteTaskSync(request, dataset);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 参数校验
     */
    private void validateRequest(IngestRequest request) {
        if (request.getDatasetId() == null || request.getDatasetId().isEmpty()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (request.getFileUrl() == null || request.getFileUrl().isEmpty()) {
            throw new IllegalArgumentException("fileUrl 不能为空");
        }
        if (request.getFileName() == null || request.getFileName().isEmpty()) {
            throw new IllegalArgumentException("fileName 不能为空");
        }
        if (request.getFileType() == null || request.getFileType().isEmpty()) {
            throw new IllegalArgumentException("fileType 不能为空");
        }
    }

    /**
     * 前置校验：查询 Dataset 配置并校验规则
     * 
     * @param request 入库请求
     * @return Dataset 详情
     */
    private DifyDatasetDetail validateAndGetDataset(IngestRequest request) {
        try {
            DifyDatasetDetail dataset = difyClient.getDatasetDetail(request.getDatasetId());
            
            log.info("获取 Dataset 详情成功: indexingTechnique={}, docForm={}", 
                    dataset.getIndexingTechnique(), dataset.getDocForm());
            
            // GENERAL 模式下校验参数
            if ("GENERAL".equalsIgnoreCase(request.getChunkingMode())) {
                if (request.getSeparator() == null || request.getSeparator().isEmpty()) {
                    throw new IllegalArgumentException("GENERAL 模式下必须指定 separator");
                }
                if (request.getMaxTokens() != null && (request.getMaxTokens() < 50 || request.getMaxTokens() > 4000)) {
                    throw new IllegalArgumentException("maxTokens 必须在 50-4000 之间");
                }
            }
            
            // PARENT_CHILD 模式下校验参数
            if ("PARENT_CHILD".equalsIgnoreCase(request.getChunkingMode())) {
                if (request.getParentSeparator() == null || request.getParentSeparator().isEmpty()) {
                    throw new IllegalArgumentException("PARENT_CHILD 模式下必须指定 parentSeparator");
                }
                if (request.getSubSeparator() == null || request.getSubSeparator().isEmpty()) {
                    throw new IllegalArgumentException("PARENT_CHILD 模式下必须指定 subSeparator");
                }
                if (request.getParentMaxTokens() != null && (request.getParentMaxTokens() < 50 || request.getParentMaxTokens() > 4000)) {
                    throw new IllegalArgumentException("parentMaxTokens 必须在 50-4000 之间");
                }
                if (request.getSubMaxTokens() != null && (request.getSubMaxTokens() < 50 || request.getSubMaxTokens() > 4000)) {
                    throw new IllegalArgumentException("subMaxTokens 必须在 50-4000 之间");
                }
            }
            
            return dataset;
        } catch (Exception e) {
            log.error("规则校验失败", e);
            throw new RuntimeException("规则校验失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文档入库（兼容旧接口，默认异步）
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocument(@RequestBody IngestRequest request) {
        return ingestDocumentAsync(request);
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        return taskRepository.findById(UUID.fromString(taskId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询任务日志
     */
    @GetMapping("/task/{taskId}/logs")
    public ResponseEntity<?> getTaskLogs(@PathVariable String taskId) {
        return ResponseEntity.ok(taskLogRepository.findByTaskIdOrderByCreatedAtDesc(UUID.fromString(taskId)));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
