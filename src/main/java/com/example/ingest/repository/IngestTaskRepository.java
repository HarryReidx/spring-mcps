package com.example.ingest.repository;

import com.example.ingest.entity.IngestTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 任务管理 Repository
 */
@Repository
public interface IngestTaskRepository extends 
        CrudRepository<IngestTask, UUID>, 
        PagingAndSortingRepository<IngestTask, UUID> {
    
    /**
     * 根据状态查询任务（分页）
     */
    Page<IngestTask> findByStatus(IngestTask.TaskStatus status, Pageable pageable);
    
    /**
     * 根据执行模式查询任务（分页）
     */
    Page<IngestTask> findByExecutionMode(IngestTask.ExecutionMode executionMode, Pageable pageable);
    
    /**
     * 根据 datasetId 查询任务（分页）
     */
    Page<IngestTask> findByDatasetId(String datasetId, Pageable pageable);
    
    /**
     * 统计各状态任务数量
     */
    @Query("SELECT COUNT(*) FROM mcp_ingest_tasks WHERE status = :status")
    long countByStatus(String status);
    
    /**
     * 查询最近的任务
     */
    @Query("SELECT * FROM mcp_ingest_tasks ORDER BY created_at DESC LIMIT :limit")
    List<IngestTask> findRecentTasks(int limit);
}
