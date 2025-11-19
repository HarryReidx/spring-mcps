# Dify Document Ingest Service

Spring Boot 服务，用于处理文档解析并写入 Dify 知识库，集成 MinerU 和 MinIO。

## 功能

- 下载文档文件（支持 PDF、DOC、DOCX、PPT、PPTX）
- 调用 MinerU 解析 PDF 并提取图片
- 从 PostgreSQL 查询图片真实路径
- 替换 markdown 中的临时图片 URL 为 MinIO 永久 URL
- 调用 Dify 知识库 API 写入文档

## 快速开始

### 配置

编辑 `src/main/resources/application.yml`，配置 Dify、MinerU、PostgreSQL 和 MinIO 相关参数。

### 运行

```bash
mvn spring-boot:run
```

### 调用示例

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "dataset-123",
    "fileUrl": "http://example.com/file.pdf",
    "fileName": "sample.pdf",
    "fileType": "pdf"
  }'
```

### 响应示例

```json
{
  "success": true,
  "message": "Ingest document success",
  "fileIds": ["file-id-123"],
  "stats": {
    "chunkCount": 1,
    "imageCount": 5
  }
}
```

## 故障排查

### MinerU 接口 422 错误

如果遇到 `422 Unprocessable Entity` 错误，说明 MinerU 接口路径或参数不正确。

**快速测试：**

1. 测试 MinerU 连接：
```bash
curl http://localhost:8080/api/test/mineru
```

2. 批量测试常见配置：
```bash
curl -X POST http://localhost:8080/api/test/mineru/batch \
  -H "Content-Type: application/json" \
  -d '{"fileUrl": "http://example.com/test.pdf"}'
```

3. 查看日志找到成功的配置（标记为 ✓），然后更新 `MineruClient.java`

详细说明请查看 [MINERU-API-TEST.md](MINERU-API-TEST.md)

## 待实现

- Office 文档转 PDF 功能（在 `DocumentIngestService.convertToPdfIfNeeded` 方法中）
- 根据实际 MinerU API 调整接口路径和参数（使用测试接口快速定位）
