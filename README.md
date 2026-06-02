[English Version](./README_EN.md)

# Dify 文档入库服务

一个功能完整的 Spring Boot 服务，用于绕过 Dify 平台参数大小限制，实现完整的文档解析与知识库入库流程。

**核心亮点**：
- 🚀 完整的文档处理流程（下载 → 解析 → 增强 → 入库）
- 🤖 多 VLM 支持（OpenAI/Qwen/Ollama）进行图片语义理解
- 📊 任务管理系统（同步/异步，状态跟踪）
- 🎨 Vue 3 前端监控大屏
- ⚡ 性能优化（VLM 并发调用，超时优化）
- 🔧 灵活配置（环境变量，多种分块策略）

## 核心功能

### 文档处理
- **文件下载**：支持 HTTP/HTTPS，自动管理临时文件
- **格式转换**：自动转换为 PDF（必要时）
- **MinerU 解析**：调用 MinerU 进行图文混合解析，提取 Markdown 和图片信息
- **图片路径替换**：从 PostgreSQL 查询真实 MinIO URL，自动替换 Markdown 中的图片路径

### 语义增强 RAG
- **VLM 视觉理解**：支持 OpenAI GPT-4o、Qwen、Ollama 等多种 VLM 模型
  - 并发分析图片，提取语义描述和 OCR 文字
  - 超时优化（180s）和耗时监控
  - 失败降级处理
- **语义重写**：注入标题上下文，确保 Dify 切分后的片段包含足够信息
- **Markdown 优化**：
  - 父子分段标识替换（`#` → `{{>1#}}`）
  - 表格防截断处理
  - 自动检测分段策略

### 分块策略
- **文本模型（text_model）**：普通分段模式
- **层级模型（hierarchical_model）**：父子结构分段，支持自定义父子分段 token 数

### 任务管理
- **同步入库**：`POST /ingest/sync` - 阻塞等待，立即返回结果，适合小文件
- **异步任务**：`POST /ingest/async` - 后台处理，支持状态跟踪和进度查询
- **任务监控**：完整的任务管理系统，记录执行详情和结果
- **执行日志**：记录任务执行过程中的 INFO/WARN/ERROR 日志，支持前端查看

### Dify 入库
- 调用 Dify API 将处理后的文档写入知识库
- 支持多种索引技术和文档形式
- 动态配置分块参数

## 完整功能清单

### 文档处理能力
- ✅ HTTP/HTTPS 文件下载
- ✅ PDF 文档解析（MinerU）
- ✅ 图文混合内容提取
- ✅ 图片路径自动替换（MinIO）
- ⏳ Office 文档转 PDF（doc/docx/ppt/pptx）

### 语义增强能力
- ✅ VLM 视觉理解（图片分析）
  - ✅ OpenAI GPT-4o 支持
  - ✅ Qwen 通义千问支持
  - ✅ ModelVerse 优云智算支持
  - ✅ Ollama 本地部署支持
- ✅ 标题上下文注入
- ✅ 图片描述增强
- ✅ 父子分段优化
- ✅ 表格防截断处理
- ✅ VLM 并发调用优化
- ✅ 超时处理（180s）
- ✅ 耗时监控

### 分块策略
- ✅ 文本模型（text_model）- 普通分段
- ✅ 层级模型（hierarchical_model）- 父子结构分段
- ✅ 自定义分段参数（token 数、重叠度）
- ✅ 自动检测分段策略

### 任务管理
- ✅ 同步入库（`POST /ingest/sync` - 阻塞等待，立即返回结果）
- ✅ 异步任务（`POST /ingest/async` - 后台处理）
- ✅ 任务状态跟踪（PENDING/PROCESSING/COMPLETED/FAILED）
- ✅ 执行模式标识（SYNC/ASYNC）
- ✅ 详细执行报告
- ✅ 错误信息记录
- ✅ 执行日志系统（INFO/WARN/ERROR 级别）
- ✅ Markdown 内容存储
- ✅ 任务列表查询（分页、筛选）
- ✅ 统计信息查询

### 前端监控
- ✅ Vue 3 + Element Plus
- ✅ 统计概览页（总任务数、成功率、平均耗时）
- ✅ 任务列表页（分页、状态筛选、执行模式筛选）
- ✅ 任务详情页（Markdown 预览、语法高亮）
- ✅ 执行日志面板（实时查看任务日志，支持日志级别标识）
- ✅ VLM 耗时显示（修复数字显示问题）
- ✅ 一键复制功能
- ✅ 响应式设计

### 系统特性
- ✅ 优雅降级（VLM 失败不影响主流程）
- ✅ 全局异常处理
- ✅ 灵活配置（环境变量覆盖）
- ✅ 精确图片路径替换
- ✅ 请求清理过滤器（处理特殊字符）
- ✅ 异步任务线程池
- ✅ 数据库连接池
- ✅ Actuator 监控端点

### API 接口
- ✅ 同步文档入库接口（`POST /ingest/sync`）
- ✅ 异步任务创建接口（`POST /ingest/async`）
- ✅ 任务详情查询接口
- ✅ 任务日志查询接口（`GET /task/{id}/logs`）
- ✅ 任务列表查询接口
- ✅ 统计信息查询接口
- ✅ 健康检查接口

### 数据库
- ✅ PostgreSQL 支持
- ✅ 任务表（ingest_tasks）
- ✅ 任务日志表（ingest_task_logs）
- ✅ 图片文件表（tool_files）
- ✅ 自动更新时间戳
- ✅ 索引优化

## 技术栈

### 后端
- Spring Boot 3.5.5
- Spring Data JDBC
- PostgreSQL 12+
- OkHttp 4.12.0
- Lombok
- Jackson

### 前端
- Vue 3 + Composition API
- Element Plus
- Vue Router
- Axios
- Highlight.js
- Vite

## 项目结构

```
com.example.ingest
├── DifyIngestApplication.java          # 启动类
├── controller/
│   ├── DocumentIngestController.java   # 文档入库接口
│   └── IngestTaskController.java       # 任务管理接口
├── service/
│   ├── DocumentIngestService.java      # 核心业务逻辑
│   ├── SemanticTextProcessor.java      # 语义文本处理器
│   └── IngestTaskService.java          # 任务管理服务
├── client/
│   ├── MineruClient.java               # MinerU 客户端
│   ├── DifyClient.java                 # Dify 客户端
│   └── VlmClient.java                  # VLM 视觉理解客户端
├── repository/
│   ├── ToolFileRepository.java         # 图片文件数据访问
│   └── IngestTaskRepository.java       # 任务数据访问
├── entity/
│   ├── ToolFile.java                   # tool_files 表实体
│   └── IngestTask.java                 # ingest_tasks 表实体
├── model/                              # DTO 模型
├── config/                             # 配置类
│   ├── AppProperties.java              # 应用配置
│   ├── AsyncConfig.java                # 异步任务配置
│   ├── ObjectMapperConfig.java         # JSON 配置
│   └── RequestCleanupFilter.java       # 请求清理过滤器
└── exception/                          # 异常处理
```

## 配置

### 核心配置（`src/main/resources/application.yml`）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://172.24.0.5:5432/dify
    username: postgres
    password: difyai123456

app:
  # Dify API 配置
  dify:
    api-key: dataset-CxGlfh0xHkUoCts6dj17XUhw
    base-url: http://172.24.0.5/v1
  
  # MinerU 服务配置
  mineru:
    base-url: http://172.24.0.5:8000
    server-type: local
    parse-method: auto
    enable-formula: true
    enable-table: true
  
  # MinIO 存储配置
  minio:
    img-path-prefix: http://172.24.0.5:9000/ty-ai-flow
  
  # 父子分段配置（层级模型）
  hierarchical:
    max-tokens: 1024         # 父分段最大 token 数
    sub-max-tokens: 512      # 子分段最大 token 数
    chunk-overlap: 50        # 分段重叠 token 数
  
  # VLM 视觉模型配置
  vlm:
    provider: qwen                                                         # 提供商: openai, qwen, modelverse
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1           # VLM API 地址
    api-key: ${VLM_API_KEY:}                                               # VLM API 密钥
    model: qwen-vl-max-latest                                              # 模型名称
    max-tokens: 10000
```

### 环境变量覆盖

```bash
# Dify 配置
export DIFY_API_KEY=your-key
export DIFY_BASE_URL=http://your-host/v1

# MinerU 配置
export MINERU_BASE_URL=http://your-host:8000

# VLM 配置（可选，仅在启用 VLM 时需要）
# 使用 Qwen（推荐）
export VLM_PROVIDER=qwen
export VLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export VLM_API_KEY=sk-xxx
export VLM_MODEL=qwen-vl-max-latest

# 或使用 ModelVerse（优云智算）
export VLM_PROVIDER=modelverse
export VLM_BASE_URL=https://api.modelverse.cn/v1
export VLM_API_KEY=your-key
export VLM_MODEL=qwen-vl-max

# 或使用 OpenAI
export VLM_PROVIDER=openai
export VLM_BASE_URL=https://api.openai.com/v1
export VLM_API_KEY=sk-xxx
export VLM_MODEL=gpt-4o
```

### 配置优先级

```
请求参数 > 环境变量 > 配置文件
```

## 快速开始

### 1. 数据库初始化
```bash
psql -U postgres -d dify -f sql/001_create_ingest_tasks_table.sql
psql -U postgres -d dify -f sql/002_create_ingest_task_logs_table.sql
```

### 2. 启动后端
```bash
mvn clean package -DskipTests
java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

### 3. 启动前端（可选）
```bash
cd frontend
npm install
npm run dev
```

### 4. 验证部署
```bash
# 后端健康检查
curl http://localhost:8080/api/dify/document/health
# 预期: OK

# 查看统计信息
curl http://localhost:8080/api/dify/tasks/stats

# 前端访问: http://localhost:5173
```

## API 接口

### 1. 同步文档入库（阻塞等待）

```http
POST /api/dify/document/ingest/sync
```

阻塞等待，立即返回最终结果，适合小文件。

**请求体**：
```json
{
  "datasetId": "xxx",
  "fileUrl": "http://xxx/file.pdf",
  "fileName": "file.pdf",
  "fileType": "pdf",
  "chunkingMode": "AUTO",               // 分块模式: AUTO（自动）| CUSTOM（自定义）
  "enableVlm": true,                    // 是否启用 VLM 图片理解
  "maxTokens": 1024,                    // CUSTOM 模式：父分段最大 token 数
  "subMaxTokens": 512,                  // CUSTOM 模式：子分段最大 token 数
  "chunkOverlap": 50,                   // CUSTOM 模式：分段重叠 token 数
  "separator": "\\n"                    // CUSTOM 模式：分隔符
}
```

**响应（成功）**：
```json
{
  "success": true,
  "taskId": "uuid",
  "status": "COMPLETED",
  "fileIds": ["doc-id-xxx"],
  "stats": {
    "imageCount": 5,
    "chunkCount": 10
  },
  "vlmCostTime": 5000,
  "totalCostTime": 30000
}
```

**响应（失败）**：
```json
{
  "success": false,
  "taskId": "uuid",
  "status": "FAILED",
  "errorMsg": "错误信息"
}
```

### 2. 异步文档入库（后台处理）

```http
POST /api/dify/document/ingest/async
```

立即返回任务 ID，后台处理，适合大文件。

**请求体**：同上

**响应**：
```json
{
  "taskId": "uuid",
  "status": "PENDING"
}
```

### 3. 文档入库（兼容旧接口）

```http
POST /api/dify/document/ingest
```

默认异步模式，等同于 `/ingest/async`。

### 4. 查询任务详情

```http
GET /api/dify/document/task/{taskId}
```

**响应**：
```json
{
  "id": "uuid",
  "datasetId": "xxx",
  "fileName": "test.pdf",
  "fileUrl": "http://xxx/test.pdf",
  "fileType": "pdf",
  "status": "COMPLETED",
  "executionMode": "ASYNC",
  "enableVlm": true,
  "startTime": "2025-11-26T10:00:00",
  "endTime": "2025-11-26T10:05:00",
  "createdAt": "2025-11-26T09:55:00",
  "updatedAt": "2025-11-26T10:05:00",
  "vlmCostTime": 5000,
  "totalCostTime": 30000,
  "resultSummary": "{\"imageCount\":5,\"chunkCount\":10,\"fileIds\":[\"doc-xxx\"]}",
  "parsedMarkdown": "# 标题\n内容...",
  "errorMsg": null
}
```

### 5. 查询任务日志

```http
GET /api/dify/document/task/{taskId}/logs
```

**响应**：
```json
[
  {
    "id": "uuid",
    "taskId": "uuid",
    "logLevel": "INFO",
    "logMessage": "开始处理文档",
    "logDetail": "indexingTechnique=high_quality, docForm=hierarchical_model",
    "createdAt": "2025-11-26T10:00:00"
  },
  {
    "id": "uuid",
    "taskId": "uuid",
    "logLevel": "WARN",
    "logMessage": "图片替换失败",
    "logDetail": "未找到图片 image1.png 的 file_key",
    "createdAt": "2025-11-26T10:00:05"
  }
]
```

### 6. 查询任务列表

```http
GET /api/dify/tasks?page=0&size=20&status=COMPLETED&mode=ASYNC
```

**参数**：
- `page` - 页码（从 0 开始，默认 0）
- `size` - 每页数量（默认 20）
- `status` - 状态筛选（可选：PENDING, PROCESSING, COMPLETED, FAILED）
- `mode` - 执行模式筛选（可选：SYNC, ASYNC）

**响应**：
```json
{
  "content": [
    {
      "id": "uuid",
      "datasetId": "xxx",
      "fileName": "test.pdf",
      "status": "COMPLETED",
      "executionMode": "ASYNC",
      "enableVlm": true,
      "vlmCostTime": 5000,
      "totalCostTime": 30000,
      "createdAt": "2025-11-26T10:00:00"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

### 7. 获取统计信息

```http
GET /api/dify/tasks/stats
```

**响应**：
```json
{
  "totalCount": 100,
  "completedCount": 85,
  "failedCount": 5,
  "processingCount": 2,
  "pendingCount": 8,
  "successRate": "85.00%"
}
```

### 8. 健康检查

```http
GET /api/dify/document/health
```

**响应**：`OK`

---

## 接口使用示例

### 示例 1：同步入库（AUTO 模式）

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/sync \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "your-dataset-id",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "chunkingMode": "AUTO",
    "enableVlm": true
  }'
```

### 示例 2：异步入库（CUSTOM 模式）

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/async \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "your-dataset-id",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "chunkingMode": "CUSTOM",
    "separator": "\\n\\n",
    "maxTokens": 800,
    "chunkOverlap": 100,
    "enableVlm": false
  }'
```

### 示例 3：查询任务状态

```bash
# 查询任务详情
curl http://localhost:8080/api/dify/document/task/{taskId}

# 查询任务日志
curl http://localhost:8080/api/dify/document/task/{taskId}/logs
```

### 示例 4：查询任务列表

```bash
# 查询所有任务
curl http://localhost:8080/api/dify/tasks?page=0&size=20

# 查询已完成的任务
curl http://localhost:8080/api/dify/tasks?status=COMPLETED

# 查询同步模式的任务
curl http://localhost:8080/api/dify/tasks?mode=SYNC
```

### 示例 5：获取统计信息

```bash
curl http://localhost:8080/api/dify/tasks/stats
```

## 文档导航

- 📖 [README.md](README.md) - 本文件，项目总览和功能清单
- 🏗️ [ARCHITECTURE.md](ARCHITECTURE.md) - 系统架构和核心组件详解
- 📦 [DEPLOYMENT.md](DEPLOYMENT.md) - 完整部署指南
- 💻 [PROJECT-PROMPT.md](PROJECT-PROMPT.md) - 项目提示文档（开发参考）
- 🎨 [frontend/README.md](frontend/README.md) - 前端项目说明

## 核心特性

### 优雅降级
- VLM 失败不影响主流程，自动降级处理
- 详细的错误日志和友好的错误响应
- 失败任务自动记录错误信息

### 性能优化
- VLM 并发调用，10 张图片从 50s 降至 5s
- 超时优化：connectTimeout 90s, readTimeout 180s
- 数据库连接池优化
- 异步任务处理，不阻塞主线程

### 灵活配置
- 支持环境变量覆盖配置
- 支持多环境部署
- 动态分块参数配置
- 多 VLM 提供商支持

### 错误处理
- 全局异常处理
- 详细日志记录
- 自动清理 JSON 特殊字符（解决 Dify HTTP 插件非断空格问题）
- 任务失败自动记录错误信息

## 测试

### 快速测试

```bash
# 1. 健康检查
curl http://localhost:8080/api/dify/document/health

# 2. 创建异步任务
curl -X POST http://localhost:8080/api/dify/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "test-dataset",
    "fileUrl": "http://example.com/test.pdf",
    "fileName": "test.pdf",
    "fileType": "pdf",
    "docForm": "hierarchical_model",
    "enableVlm": true
  }'

# 3. 查询任务状态（替换 {taskId}）
curl http://localhost:8080/api/dify/tasks/{taskId}

# 4. 查看统计信息
curl http://localhost:8080/api/dify/tasks/stats
```

### 测试脚本

Windows:
```bash
test-api.bat
```

Linux/Mac:
```bash
chmod +x test-api.sh
./test-api.sh
```

## 部署

### 快速部署（3 步）

#### 1. 数据库初始化
```bash
psql -U postgres -d dify -f sql/001_create_ingest_tasks_table.sql
psql -U postgres -d dify -f sql/002_create_ingest_task_logs_table.sql
```

#### 2. 启动后端
```bash
mvn clean package -DskipTests
java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

#### 3. 启动前端（可选）
```bash
cd frontend
npm install
npm run dev
```

详细部署指南：[DEPLOYMENT.md](DEPLOYMENT.md)

## 监控

### 后端监控

**Actuator 端点**：
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics

**日志**：
```bash
tail -f logs/spring.log
```

### 前端监控

访问：http://localhost:5173

- 统计概览：总任务数、成功率、平均耗时
- 任务列表：分页查询、状态筛选
- 任务详情：Markdown 预览、执行报告

## 使用示例

### 同步入库（阻塞等待，立即返回结果）

```bash
curl -X POST http://localhost:8080/api/dify/document/ingest/sync \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "your-dataset-id",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "docForm": "hierarchical_model",
    "enableVlm": true,
    "maxTokens": 1024,
    "subMaxTokens": 512,
    "chunkOverlap": 50
  }'
```

### 异步任务（后台处理，立即返回任务 ID）

```bash
# 创建任务
curl -X POST http://localhost:8080/api/dify/document/ingest/async \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "your-dataset-id",
    "fileUrl": "http://example.com/document.pdf",
    "fileName": "document.pdf",
    "fileType": "pdf",
    "docForm": "hierarchical_model",
    "enableVlm": true
  }'

# 查询任务状态
curl http://localhost:8080/api/dify/document/task/{taskId}

# 查询任务日志
curl http://localhost:8080/api/dify/document/task/{taskId}/logs

# 查看统计信息
curl http://localhost:8080/api/dify/tasks/stats
```

## 前端监控大屏

项目包含完整的 Vue 3 前端监控系统，提供可视化的任务管理界面。

### 功能特性

- **统计概览**：总任务数、成功率、各状态任务数
- **任务列表**：分页查询、状态筛选、执行模式筛选、快速跳转
- **任务详情**：基本信息、结果摘要、Markdown 预览（语法高亮）、执行日志面板

### 快速启动

```bash
cd frontend
npm install
npm run dev
```

访问：http://localhost:5173

### 技术栈

- Vue 3 + Composition API
- Element Plus UI 组件库
- Vue Router 路由管理
- Axios HTTP 客户端
- Highlight.js 语法高亮

## 故障排查

### 常见问题

**Q: 数据库连接失败？**
```bash
# 检查数据库配置
# application.yml 中的 spring.datasource.url/username/password

# 测试连接
psql -U postgres -d dify -h 172.24.0.5
```

**Q: VLM 请求超时？**
- 检查 VLM 服务是否正常
- 检查网络连接
- 图片大小建议 < 5MB
- 已优化超时时间到 180s

**Q: 任务一直 PROCESSING？**
```bash
# 查看后端日志
tail -f logs/spring.log

# 检查 MinerU 服务
curl http://172.24.0.5:8000/docs
```

**Q: 前端无法访问？**
- 检查后端是否启动（8080）
- 检查前端是否启动（5173）
- 检查代理配置（vite.config.js）

### 日志查看

```bash
# 后端日志
tail -f logs/spring.log

# 数据库查询
psql -U postgres -d dify
SELECT id, file_name, status, created_at FROM ingest_tasks ORDER BY created_at DESC LIMIT 10;
```

## 待实现功能

- [ ] Office 文档转 PDF（doc/docx/ppt/pptx）
- [ ] 大文件分片上传
- [ ] VLM 调用重试和缓存机制
- [ ] 批量处理接口
- [ ] API 认证和限流
