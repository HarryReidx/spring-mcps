# Dify 文档入库服务 - 项目总结

## 项目概述

这是一个 Spring Boot 3 服务，用于处理文档解析并写入 Dify 知识库。服务集成了 MinerU（PDF 解析）、PostgreSQL（图片元数据）和 MinIO（图片存储）。

## 核心功能

1. **文档下载**：从 URL 下载文件
2. **格式转换**：将 Office 文档转换为 PDF（预留接口）
3. **PDF 解析**：调用 MinerU 解析 PDF，提取文本和图片
4. **图片处理**：从数据库查询图片真实路径，替换 markdown 中的临时 URL
5. **知识库入库**：调用 Dify API 写入文档

## 项目结构

```
com.example.difyingest/
├── DifyIngestApplication.java          # 启动类
├── controller/
│   ├── DocumentIngestController.java   # 主接口 Controller
│   └── HealthController.java           # 健康检查
├── service/
│   └── DocumentIngestService.java      # 核心业务逻辑
├── client/
│   ├── MineruClient.java               # MinerU 客户端
│   └── DifyClient.java                 # Dify 客户端
├── repository/
│   └── ToolFileRepository.java         # 数据库访问
├── model/                              # DTO 模型
│   ├── DocumentIngestRequest.java
│   ├── DocumentIngestResponse.java
│   ├── MineruParseResponse.java
│   ├── DifyCreateDocumentRequest.java
│   ├── DifyCreateDocumentResponse.java
│   └── ToolFile.java
├── config/
│   ├── AppProperties.java              # 配置属性
│   └── WebClientConfig.java            # WebClient 配置
└── exception/
    └── GlobalExceptionHandler.java     # 全局异常处理
```

## API 接口

### POST /api/dify/document/ingest

**请求体：**
```json
{
  "datasetId": "dataset-123",
  "fileUrl": "http://example.com/file.pdf",
  "fileName": "sample.pdf",
  "fileType": "pdf"
}
```

**响应体：**
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

## 配置说明

所有配置在 `application.yml` 中，支持环境变量覆盖：

- `app.dify.base-url`: Dify 服务地址
- `app.dify.api-key`: Dify API Key（可通过 `DIFY_API_KEY` 环境变量覆盖）
- `app.mineru.base-url`: MinerU 服务地址（可通过 `MINERU_BASE_URL` 环境变量覆盖）
- `app.img.path-prefix`: MinIO 图片访问前缀
- `app.segmentation.*`: 文档分段参数

## 待实现功能

1. **Office 文档转 PDF**：在 `DocumentIngestService.convertToPdfIfNeeded()` 方法中实现
   - 可选方案：LibreOffice 命令行、JODConverter、Apache POI + PDFBox
   
2. **MinerU API 对接**：根据实际 MinerU API 文档调整
   - 接口路径（当前为 `/file_parse`）
   - 请求参数格式
   - 响应字段映射

## 技术栈

- Java 17
- Spring Boot 3.5.5
- Spring Data JDBC
- PostgreSQL
- WebClient (Reactive HTTP Client)
- Lombok
- JODConverter (可选)
- Apache PDFBox (可选)

## 运行方式

```bash
# 使用 Maven
mvn spring-boot:run

# 或使用 Maven Wrapper
./mvnw spring-boot:run  # Linux/Mac
.\mvnw.cmd spring-boot:run  # Windows
```

## 测试调用

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

## 注意事项

1. 确保 PostgreSQL 中存在 `tool_files` 表，包含 `id` 和 `file_key` 字段
2. MinerU 服务需要提前部署并配置正确的地址
3. Dify API Key 需要有知识库写入权限
4. 建议通过环境变量管理敏感配置（API Key、数据库密码等）
