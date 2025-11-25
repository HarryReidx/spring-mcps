package com.example.ingest.service;

import com.example.ingest.entity.IngestTask;
import com.example.ingest.model.IngestRequest;
import com.example.ingest.model.IngestResponse;
import com.example.ingest.repository.IngestTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务管理服务
 * 负责任务的创建、更新、查询和异步执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestTaskService {
    
    private final IngestTaskRepository taskRepository;
    private final DocumentIngestService documentIngestService;
    private final ObjectMapper objectMapper;
    
    /**
     * 创建任务并异步执行
     * 
     * @param request 入库请求
     * @return 任务 ID
     */
    public UUID createAndExecuteTask(IngestRequest request) {
        // 1. 创建任务记录
        IngestTask task = IngestTask.builder()
                .datasetId(request.getDatasetId())
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileType(request.getFileType())
                .enableVlm(request.getEnableVlm())
                .status(IngestTask.TaskStatus.PENDING)
                .executionMode(IngestTask.ExecutionMode.ASYNC)  // 异步模式
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        task = taskRepository.save(task);
        log.info("创建异步任务: id={}, fileName={}", task.getId(), task.getFileName());
        
        // 2. 异步执行任务
        executeTaskAsync(task.getId(), request);
        
        return task.getId();
    }
    
    /**
     * 同步执行任务并入库
     * 
     * @param request 入库请求
     * @return 入库响应
     */
    public IngestResponse executeTaskSync(IngestRequest request) {
        log.info("开始同步执行任务: fileName={}", request.getFileName());
        
        // 1. 创建任务记录
        IngestTask task = IngestTask.builder()
                .datasetId(request.getDatasetId())
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileType(request.getFileType())
                .enableVlm(request.getEnableVlm())
                .status(IngestTask.TaskStatus.PROCESSING)
                .executionMode(IngestTask.ExecutionMode.SYNC)  // 同步模式
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        task = taskRepository.save(task);
        log.info("创建同步任务: id={}, fileName={}", task.getId(), task.getFileName());
        
        try {
            // 2. 执行文档入库
            IngestResponse response = documentIngestService.ingestDocument(request, IngestTask.ExecutionMode.SYNC);
            
            // 3. 更新任务结果
            if (response.isSuccess()) {
                updateTaskSuccess(task.getId(), response);
            } else {
                updateTaskFailure(task.getId(), response.getErrorMsg());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("同步任务执行失败: {}", task.getId(), e);
            updateTaskFailure(task.getId(), e.getMessage());
            
            // 返回失败响应
            return IngestResponse.builder()
                    .success(false)
                    .errorMsg(e.getMessage())
                    .fileIds(java.util.Collections.emptyList())
                    .build();
        }
    }
    
    /**
     * 异步执行任务
     */
    @Async
    public void executeTaskAsync(UUID taskId, IngestRequest request) {
        log.info("开始异步执行任务: {}", taskId);
        
        // 1. 更新状态为 PROCESSING
        updateTaskStatus(taskId, IngestTask.TaskStatus.PROCESSING, LocalDateTime.now(), null);
        
        try {
            // 2. 执行文档入库
            IngestResponse response = documentIngestService.ingestDocument(request, IngestTask.ExecutionMode.ASYNC);
            
            // 3. 更新任务结果
            if (response.isSuccess()) {
                updateTaskSuccess(taskId, response);
            } else {
                updateTaskFailure(taskId, response.getErrorMsg());
            }
            
        } catch (Exception e) {
            log.error("任务执行失败: {}", taskId, e);
            updateTaskFailure(taskId, e.getMessage());
        }
    }
    
    /**
     * 更新任务状态
     */
    private void updateTaskStatus(UUID taskId, IngestTask.TaskStatus status, 
                                   LocalDateTime startTime, LocalDateTime endTime) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            if (startTime != null) {
                task.setStartTime(startTime);
            }
            if (endTime != null) {
                task.setEndTime(endTime);
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }
    
    /**
     * 更新任务成功结果
     */
    private void updateTaskSuccess(UUID taskId, IngestResponse response) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(IngestTask.TaskStatus.COMPLETED);
            task.setEndTime(LocalDateTime.now());
            
            // 构建结果摘要
            Map<String, Object> summary = new HashMap<>();
            if (response.getStats() != null) {
                summary.put("imageCount", response.getStats().getImageCount());
                summary.put("chunkCount", response.getStats().getChunkCount());
            }
            summary.put("fileIds", response.getFileIds());
            
            try {
                task.setResultSummary(objectMapper.writeValueAsString(summary));
            } catch (Exception e) {
                log.error("序列化结果摘要失败", e);
            }
            
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            
            log.info("任务执行成功: {}", taskId);
        });
    }
    
    /**
     * 更新任务失败信息
     */
    private void updateTaskFailure(UUID taskId, String errorMsg) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(IngestTask.TaskStatus.FAILED);
            task.setEndTime(LocalDateTime.now());
            task.setErrorMsg(errorMsg);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            
            log.error("任务执行失败: {}, 错误: {}", taskId, errorMsg);
        });
    }
    
    /**
     * 查询任务详情
     */
    public IngestTask getTask(UUID taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }
    
    /**
     * 分页查询任务列表
     */
    public Page<IngestTask> getTasks(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }
    
    /**
     * 根据状态查询任务
     */
    public Page<IngestTask> getTasksByStatus(IngestTask.TaskStatus status, Pageable pageable) {
        return taskRepository.findByStatus(status, pageable);
    }
    
    /**
     * 根据执行模式查询任务
     */
    public Page<IngestTask> getTasksByExecutionMode(IngestTask.ExecutionMode executionMode, Pageable pageable) {
        return taskRepository.findByExecutionMode(executionMode, pageable);
    }
    
    /**
     * 获取任务统计信息
     */
    public Map<String, Object> getTaskStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalCount = taskRepository.count();
        long completedCount = taskRepository.countByStatus("COMPLETED");
        long failedCount = taskRepository.countByStatus("FAILED");
        long processingCount = taskRepository.countByStatus("PROCESSING");
        long pendingCount = taskRepository.countByStatus("PENDING");
        
        stats.put("totalCount", totalCount);
        stats.put("completedCount", completedCount);
        stats.put("failedCount", failedCount);
        stats.put("processingCount", processingCount);
        stats.put("pendingCount", pendingCount);
        
        // 计算成功率
        double successRate = totalCount > 0 ? (double) completedCount / totalCount * 100 : 0;
        stats.put("successRate", String.format("%.2f%%", successRate));
        
        return stats;
    }
}
