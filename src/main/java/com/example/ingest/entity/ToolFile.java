package com.example.ingest.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * tool_files 表实体
 * 用于查询图片的真实存储路径
 */
@Data
@Table("tool_files")
public class ToolFile {
    /** 主键 ID */
    @Id
    private String id;
    
    /** 文件名称（如 image_0.jpg） */
    private String name;
    
    /** MinIO 存储的文件 key */
    private String fileKey;
}
