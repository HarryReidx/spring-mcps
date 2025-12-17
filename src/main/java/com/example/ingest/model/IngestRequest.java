package com.example.ingest.model;

import lombok.Data;

/**
 * 文档入库请求
 * 由 Dify HTTP 插件调用
 * 
 * @author HarryReid(黄药师)
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
    
    /** 分块模式: AUTO, GENERAL, PARENT_CHILD */
    private String chunkingMode = "AUTO";
    
    // GENERAL 模式参数
    /** 分隔符（GENERAL 模式） */
    private String separator;
    
    /** 最大 token 数（GENERAL 模式） */
    private Integer maxTokens;
    
    /** 分块重叠（GENERAL 模式） */
    private Integer chunkOverlap;
    
    // PARENT_CHILD 模式参数
    /** 父段分隔符（PARENT_CHILD 模式） */
    private String parentSeparator;
    
    /** 父段最大 token 数（PARENT_CHILD 模式） */
    private Integer parentMaxTokens;
    
    /** 子段分隔符（PARENT_CHILD 模式） */
    private String subSeparator;
    
    /** 子段最大 token 数（PARENT_CHILD 模式） */
    private Integer subMaxTokens;
    
    /** 分块重叠（PARENT_CHILD 模式） */
    private Integer parentChunkOverlap;
    
    /** 是否启用 VLM 图片理解 */
    private Boolean enableVlm = false;
    
    /** 索引技术: high_quality, economy（从 Dataset 自动获取，无需传入） */
    private String indexingTechnique;
}
