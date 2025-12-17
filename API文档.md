# Dify Ingest API 文档

## 概述

Dify Ingest 服务提供文档入库能力，支持 PDF 文档解析、图片语义理解、智能分段和向量化入库。

**Base URL**: `http://your-server:8080`

---

## 接口列表

### 1. 异步文档入库

立即返回任务 ID，后台异步处理文档。

**接口**: `POST /api/dify/document/ingest/async`

**请求体**:

```json
{
  "datasetId": "string",
  "fileUrl": "string",
  "fileName": "string",
  "fileType": "string",
  "chunkingMode": "AUTO|GENERAL|PARENT_CHILD",
  "enableVlm": boolean,
  
  // GENERAL 模式参数
  "separator": "string",
  "maxTokens": integer,
  "chunkOverlap": integer,
  
  // PARENT_CHILD 模式参数
  "parentSeparator": "string",
  "parentMaxTokens": integer,
  "subSeparator": "string",
  "subMaxTokens": integer,
  "parentChunkOverlap": integer
}
```

**参数说明**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| datasetId | string | 是 | Dify 知识库 ID |
| fileUrl | string | 是 | 文件下载地址（HTTP/HTTPS） |
| fileName | string | 是 | 文件名称 |
| fileType | string | 是 | 文件类型（pdf, doc, docx 等） |
| chunkingMode | string | 否 | 分块模式，默认 AUTO |
| enableVlm | boolean | 否 | 是否启用图片语义理解，默认 false |

**分块模式说明**:

- **AUTO**: 自动根据知识库 `docForm` 选择分块策略
  - `parent_child` → 父子分段
  - 其他 → 通用分块
- **GENERAL**: 通用文本分块（文档经 MinerU 解析为 Markdown）
  - 必填参数: `separator`, `maxTokens`, `chunkOverlap`
  - 推荐 `separator: "# "` (一级标题) 按章节分段
  - 或使用 `separator: "\n\n"` (双换行) 按段落分段
- **PARENT_CHILD**: 父子结构分段
  - 必填参数: `parentSeparator`, `subSeparator`, `parentMaxTokens`, `subMaxTokens`, `parentChunkOverlap`

**响应示例**:

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

---

### 2. 同步文档入库

阻塞等待处理完成，返回完整结果。

**接口**: `POST /api/dify/document/ingest/sync`

**请求体**: 同异步接口

**响应示例**:

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "documentId": "doc-abc123",
  "batchId": "batch-xyz789",
  "duration": 45000,
  "message": "文档入库成功"
}
```

---

### 3. 查询任务详情

**接口**: `GET /api/dify/document/task/{taskId}`

**响应示例**:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "datasetId": "dataset-123",
  "fileName": "example.pdf",
  "status": "SUCCESS",
  "documentId": "doc-abc123",
  "batchId": "batch-xyz789",
  "fileSize": 1048576,
  "vlmErrors": 0,
  "createdAt": "2024-12-11T10:00:00",
  "completedAt": "2024-12-11T10:01:30"
}
```

---

### 4. 查询任务日志

**接口**: `GET /api/dify/document/task/{taskId}/logs`

**响应示例**:

```json
[
  {
    "id": 1,
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "level": "INFO",
    "message": "开始处理文档",
    "details": "indexingTechnique=high_quality, docForm=parent_child",
    "createdAt": "2024-12-11T10:00:00"
  },
  {
    "id": 2,
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "level": "INFO",
    "message": "文件下载完成",
    "details": "大小: 1048576 bytes",
    "createdAt": "2024-12-11T10:00:15"
  }
]
```

---

## 使用示例

### 示例 1: AUTO 模式（推荐）

自动根据知识库配置选择分块策略。

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/async \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "dataset-123",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "chunkingMode": "AUTO",
    "enableVlm": true
  }'
```

### 示例 2: GENERAL 模式

通用文本分块，适用于普通文档。

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/async \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "dataset-123",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "chunkingMode": "GENERAL",
    "separator": "#",
    "maxTokens": 1000,
    "chunkOverlap": 50,
    "enableVlm": true
  }'
```

### 示例 3: PARENT_CHILD 模式

父子结构分段，适用于技术文档、手册等有明确章节结构的文档。

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/async \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "dataset-123",
    "fileUrl": "http://example.com/manual.pdf",
    "fileName": "manual.pdf",
    "fileType": "pdf",
    "chunkingMode": "PARENT_CHILD",
    "parentSeparator": "{{>1#}}",
    "parentMaxTokens": 2048,
    "subSeparator": "{{>2#}}",
    "subMaxTokens": 1024,
    "parentChunkOverlap": 50,
    "enableVlm": true
  }'
```

### 示例 4: 查询任务状态

```bash
curl -X GET http://localhost:8080/api/dify/document/task/550e8400-e29b-41d4-a716-446655440000
```

### 示例 5: 查询任务日志

```bash
curl -X GET http://localhost:8080/api/dify/document/task/550e8400-e29b-41d4-a716-446655440000/logs
```

---

## 状态码

| 状态 | 说明 |
|------|------|
| PENDING | 任务已创建，等待处理 |
| PROCESSING | 正在处理中 |
| SUCCESS | 处理成功 |
| FAILED | 处理失败 |

---

## 错误处理

**错误响应格式**:

```json
{
  "timestamp": "2024-12-11T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "datasetId 不能为空",
  "path": "/api/dify/document/ingest/async"
}
```

**常见错误**:

| HTTP 状态码 | 错误信息 | 说明 |
|------------|---------|------|
| 400 | datasetId 不能为空 | 缺少必填参数 |
| 400 | GENERAL 模式下必须指定 separator | GENERAL 模式参数不完整 |
| 400 | PARENT_CHILD 模式下必须指定 parentSeparator | PARENT_CHILD 模式参数不完整 |
| 404 | Dataset not found | 知识库不存在 |
| 500 | Internal Server Error | 服务器内部错误 |

---

## 高级特性

### 1. VLM 图片语义理解

启用 `enableVlm: true` 后，系统会：
- 使用视觉语言模型分析文档中的图片
- 提取图片描述和 OCR 文字
- 将图片语义信息注入到 Markdown 中

### 2. LLM 摘要增强（PARENT_CHILD 模式）

在 PARENT_CHILD 模式下，系统会：
- 对每个章节的正文内容生成摘要
- 将摘要追加到父段标题后
- 提升父段的语义丰富度，改善检索效果

配置方式（`application.yml`）:

```yaml
app:
  llm:
    enabled: true
    provider: ollama
    base-url: http://localhost:11434/api/chat
    model: qwen2.5:7b
    content-threshold: 100
```

### 3. 递归预切分

防止超长子段被 Dify 硬截断，系统会：
- 在安全阈值内智能切分长段落
- 保持语义边界（优先在标点符号处切分）
- 确保每个子段都有正确的 `{{>2#}}` 前缀

---

## 配置说明

### 默认配置（`application.yml`）

```yaml
app:
  default:
    indexing-technique: high_quality
    doc-form: parent_child
  
  process-rule:
    text-model:
      separator: "\n"
      max-tokens: 1000
      chunk-overlap: 50
    
    parent-child:
      parent-separator: "{{>1#}}"
      parent-max-tokens: 2048
      sub-separator: "{{>2#}}"
      sub-max-tokens: 1024
      chunk-overlap: 50
      safe-split-threshold: 900
  
  vlm:
    provider: ollama
    base-url: http://localhost:11434/api/chat
    model: qwen2.5vl:7b
  
  llm:
    enabled: false
    provider: ollama
    base-url: http://localhost:11434/api/chat
    model: qwen2.5:7b
```

---

## 性能建议

1. **异步模式**: 大文件或批量处理建议使用异步接口
2. **VLM 开关**: 无图片或图片不重要时关闭 VLM，节省时间
3. **LLM 摘要**: 仅在需要高质量检索时启用
4. **并发控制**: 系统内部已实现 VLM 和 LLM 的并发调用

---

## 版本历史

- **v1.0** (2024-12-11): 初始版本
  - 支持 AUTO/GENERAL/PARENT_CHILD 三种分块模式
  - VLM 图片语义理解
  - LLM 摘要增强
  - 递归预切分
