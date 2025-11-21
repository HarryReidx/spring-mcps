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
}
