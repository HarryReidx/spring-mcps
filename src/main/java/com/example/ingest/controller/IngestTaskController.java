package com.example.ingest.controller;

import com.example.ingest.entity.IngestTask;
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

/**
 * 任务管理控制器
 * 提供任务列表查询、统计等接口
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@RestController
@RequestMapping("/api/dify/tasks")
@RequiredArgsConstructor
public class IngestTaskController {
    
    private final IngestTaskService taskService;
    
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
    
    /**
     * 批量删除任务
     * DELETE /api/dify/tasks
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteTasks(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<String> taskIds = (java.util.List<String>) request.get("taskIds");
        
        if (taskIds == null || taskIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "taskIds 不能为空"
            ));
        }
        
        int deletedCount = taskService.deleteTasks(taskIds);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deletedCount,
                "message", String.format("成功删除 %d 个任务", deletedCount)
        ));
    }
}
