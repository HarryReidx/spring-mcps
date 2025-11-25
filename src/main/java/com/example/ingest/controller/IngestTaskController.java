package com.example.ingest.controller;

import com.example.ingest.entity.IngestTask;
import com.example.ingest.model.IngestRequest;
import com.example.ingest.service.IngestTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 任务管理控制器
 * 提供任务的创建、查询、统计等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/dify/tasks")
@RequiredArgsConstructor
public class IngestTaskController {
    
    private final IngestTaskService taskService;
    
    /**
     * 创建异步任务
     * POST /api/dify/tasks
     * 
     * @return 任务 ID
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody IngestRequest request) {
        log.info("创建异步任务: datasetId={}, fileName={}", request.getDatasetId(), request.getFileName());
        
        UUID taskId = taskService.createAndExecuteTask(request);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "taskId", taskId.toString(),
                "message", "任务已创建，正在后台处理"
        ));
    }
    
    /**
     * 查询任务详情
     * GET /api/dify/tasks/{taskId}
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<IngestTask> getTask(@PathVariable String taskId) {
        IngestTask task = taskService.getTask(UUID.fromString(taskId));
        
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(task);
    }
    
    /**
     * 分页查询任务列表
     * GET /api/dify/tasks?page=0&size=20&status=COMPLETED&mode=ASYNC
     */
    @GetMapping
    public ResponseEntity<Page<IngestTask>> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<IngestTask> tasks;
        if (status != null && !status.isEmpty()) {
            IngestTask.TaskStatus taskStatus = IngestTask.TaskStatus.valueOf(status.toUpperCase());
            tasks = taskService.getTasksByStatus(taskStatus, pageable);
        } else if (mode != null && !mode.isEmpty()) {
            IngestTask.ExecutionMode executionMode = IngestTask.ExecutionMode.valueOf(mode.toUpperCase());
            tasks = taskService.getTasksByExecutionMode(executionMode, pageable);
        } else {
            tasks = taskService.getTasks(pageable);
        }
        
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * 获取任务统计信息
     * GET /api/dify/tasks/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = taskService.getTaskStats();
        return ResponseEntity.ok(stats);
    }
}
