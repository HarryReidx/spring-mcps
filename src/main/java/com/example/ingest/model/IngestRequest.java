package com.example.ingest.model;

import lombok.Data;

/**
 * 文档入库请求
 * 由 Dify HTTP 插件调用
 */
@Data
public class IngestRequest {
    /** Dify 知识库 ID */
    private String datasetId;
    
    /** 文件下载地址 */
    private String fileUrl;
    
    /** 文件名称 */
    private String fileName;
    
    /** 文件类型（如 pdf, doc, docx 等） */
    private String fileType;
    
    // ========== RAG 配置参数 ==========
    
    /** 分块模式: AUTO, CUSTOM */
    private String chunkingMode = "AUTO";
    
    /** 最大 token 数（CUSTOM 模式下生效） */
    private Integer maxTokens;
    
    /** 分块重叠（CUSTOM 模式下生效） */
    private Integer chunkOverlap;
    
    /** 分隔符（CUSTOM 模式下生效） */
    private String separator;
    
    /** 是否启用 VLM 图片理解 */
    private Boolean enableVlm = false;
    
    /** 索引技术: high_quality, economy */
    private String indexingTechnique = "high_quality";
    
    /** 文档形式: text_model, hierarchical_model */
    private String docForm = "text_model";
}
