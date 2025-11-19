package com.example.difyingest.model;

import lombok.Data;

/**
 * 文档入库请求
 * 
 * curl 示例：
 * curl -X POST http://localhost:8080/api/dify/document/ingest \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "datasetId": "dataset-123",
 *     "fileUrl": "http://example.com/file.pdf",
 *     "fileName": "sample.pdf",
 *     "fileType": "pdf"
 *   }'
 */
@Data
public class DocumentIngestRequest {
    private String datasetId;
    private String fileUrl;
    private String fileName;
    private String fileType;
}
