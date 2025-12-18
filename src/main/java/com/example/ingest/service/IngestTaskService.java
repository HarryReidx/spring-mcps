package com.example.ingest.service;

import com.example.ingest.entity.IngestTask;
import com.example.ingest.model.DifyDatasetDetail;
import com.example.ingest.model.IngestRequest;
import com.example.ingest.model.IngestResponse;
import com.example.ingest.repository.IngestTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestTaskService {
    
    private final IngestTaskRepository taskRepository;
    private final DocumentIngestService documentIngestService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 创建任务并异步执行
     * 
     * @param request 入库请求
     * @param dataset Dataset 详情
     * @return 任务 ID
     */
    public UUID createAndExecuteTask(IngestRequest request, DifyDatasetDetail dataset) {
        IngestTask task = IngestTask.builder()
                .datasetId(request.getDatasetId())
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileType(request.getFileType())
                .enableVlm(request.getEnableVlm())
                .status(IngestTask.TaskStatus.PENDING)
                .executionMode(IngestTask.ExecutionMode.ASYNC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        task = taskRepository.save(task);
        log.info("创建异步任务: id={}, fileName={}", task.getId(), task.getFileName());
        
        // 通过 Spring 代理调用异步方法
        IngestTaskService self = applicationContext.getBean(IngestTaskService.class);
        self.executeTaskAsync(task.getId(), request, dataset);
        
        return task.getId();
    }

    /**
     * 创建任务并同步执行
     * 
     * @param request 入库请求
     * @param dataset Dataset 详情
     * @return 执行结果
     */
    public Map<String, Object> createAndExecuteTaskSync(IngestRequest request, DifyDatasetDetail dataset) {
        IngestTask task = IngestTask.builder()
                .datasetId(request.getDatasetId())
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileType(request.getFileType())
                .enableVlm(request.getEnableVlm())
                .status(IngestTask.TaskStatus.PROCESSING)
                .executionMode(IngestTask.ExecutionMode.SYNC)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        task = taskRepository.save(task);
        log.info("创建同步任务: id={}, fileName={}", task.getId(), task.getFileName());
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getId().toString());
        
        try {
            IngestResponse ingestResponse = documentIngestService.ingestDocument(request, IngestTask.ExecutionMode.SYNC, task.getId(), dataset);
            
            if (ingestResponse.isSuccess()) {
                updateTaskSuccess(task.getId(), ingestResponse);
                response.put("success", true);
                response.put("status", "COMPLETED");
                response.put("fileIds", ingestResponse.getFileIds());
                response.put("stats", ingestResponse.getStats());
                response.put("vlmCostTime", ingestResponse.getVlmCostTime());
                response.put("mineruCostTime", ingestResponse.getMineruCostTime());
                response.put("downloadCostTime", ingestResponse.getDownloadCostTime());
                response.put("totalCostTime", ingestResponse.getTotalCostTime());
            } else {
                updateTaskFailure(task.getId(), ingestResponse.getErrorMsg());
                response.put("success", false);
                response.put("status", "FAILED");
                response.put("errorMsg", ingestResponse.getErrorMsg());
            }
            
        } catch (Exception e) {
            log.error("同步任务执行失败: {}", task.getId(), e);
            updateTaskFailure(task.getId(), e.getMessage());
            response.put("success", false);
            response.put("status", "FAILED");
            response.put("errorMsg", e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 异步执行任务
     * 
     * @param taskId 任务 ID
     * @param request 入库请求
     * @param dataset Dataset 详情
     */
    @Async
    public void executeTaskAsync(UUID taskId, IngestRequest request, DifyDatasetDetail dataset) {
        log.info("开始异步执行任务: {}", taskId);
        
        // 1. 更新状态为 PROCESSING
        updateTaskStatus(taskId, IngestTask.TaskStatus.PROCESSING, LocalDateTime.now(), null);
        
        try {
            // 2. 执行文档入库
            IngestResponse response = documentIngestService.ingestDocument(request, IngestTask.ExecutionMode.ASYNC, taskId, dataset);
            
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
            task.setVlmCostTime(response.getVlmCostTime());
            task.setMineruCostTime(response.getMineruCostTime());
            task.setDownloadCostTime(response.getDownloadCostTime());
            task.setTotalCostTime(response.getTotalCostTime());
            task.setFileSize(response.getFileSize());
            
            // 保存 VLM 失败图片列表
            if (response.getVlmFailedImages() != null && !response.getVlmFailedImages().isEmpty()) {
                try {
                    task.setVlmFailedImages(objectMapper.writeValueAsString(response.getVlmFailedImages()));
                } catch (Exception e) {
                    log.error("序列化 VLM 失败图片列表失败", e);
                }
            }
            
            // 构建结果摘要
            Map<String, Object> summary = new HashMap<>();
            if (response.getStats() != null) {
                summary.put("imageCount", response.getStats().getImageCount());
            }
            if (response.getFileIds() != null && !response.getFileIds().isEmpty()) {
                summary.put("fileIds", response.getFileIds().get(0));
            }
            
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
    
    /**
     * 批量删除任务
     * 
     * @param taskIds 任务 ID 列表
     * @return 删除数量
     */
    public int deleteTasks(java.util.List<String> taskIds) {
        int deletedCount = 0;
        for (String taskId : taskIds) {
            try {
                UUID uuid = UUID.fromString(taskId);
                if (taskRepository.existsById(uuid)) {
                    taskRepository.deleteById(uuid);
                    deletedCount++;
                    log.info("删除任务: {}", taskId);
                }
            } catch (Exception e) {
                log.error("删除任务失败: {}", taskId, e);
            }
        }
        return deletedCount;
    }
}
