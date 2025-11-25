package com.example.ingest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档入库任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ingest_tasks")
public class IngestTask {
    
    @Id
    private UUID id;
    
    @Column("dataset_id")
    private String datasetId;
    
    @Column("file_name")
    private String fileName;
    
    @Column("file_url")
    private String fileUrl;
    
    @Column("file_type")
    private String fileType;
    
    @Column("status")
    private TaskStatus status;
    
    @Column("execution_mode")
    private ExecutionMode executionMode;  // 执行模式：SYNC 或 ASYNC
    
    @Column("enable_vlm")
    private Boolean enableVlm;
    
    @Column("start_time")
    private LocalDateTime startTime;
    
    @Column("end_time")
    private LocalDateTime endTime;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("updated_at")
    private LocalDateTime updatedAt;
    
    @Column("error_msg")
    private String errorMsg;
    
    @Column("result_summary")
    private String resultSummary;  // JSON 字符串
    
    @Column("original_doc_url")
    private String originalDocUrl;
    
    @Column("parsed_markdown")
    private String parsedMarkdown;
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,      // 待处理
        PROCESSING,   // 处理中
        COMPLETED,    // 已完成
        FAILED        // 失败
    }
    
    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        SYNC,   // 同步执行
        ASYNC   // 异步执行
    }
}
