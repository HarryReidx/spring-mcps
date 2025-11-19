package com.example.difyingest.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Table("tool_files")
public class ToolFile {
    @Id
    private UUID id;
    
    @Column("file_key")
    private String fileKey;
    
    /**
     * 获取 ID 的字符串表示（不含连字符）
     */
    public String getIdAsString() {
        return id != null ? id.toString().replace("-", "") : null;
    }
}
