# Dify Document Ingest Service

Spring Boot 服务，用于绕过 Dify 平台参数大小限制，实现完整的文档解析与知识库入库流程。

## 核心功能

- **文件下载**：支持 HTTP/HTTPS，自动管理临时文件
- **MinerU 解析**：调用 MinerU 进行图文混合解析，提取 Markdown 和图片信息
- **图片路径替换**：从 PostgreSQL 查询真实 MinIO URL，自动替换 Markdown 中的图片路径
- **Dify 入库**：调用 Dify API 将处理后的文档写入知识库

## 技术栈

- Spring Boot 3.5.5
- Spring Data JDBC
- PostgreSQL
- OkHttp 4.12.0
- Lombok

## 项目结构

```
com.example.ingest
├── DifyIngestApplication.java          # 启动类
├── controller/
│   └── DocumentIngestController.java   # HTTP 接口
├── service/
│   └── DocumentIngestService.java      # 核心业务逻辑
├── client/
│   ├── MineruClient.java               # MinerU 客户端
│   └── DifyClient.java                 # Dify 客户端
├── repository/
│   └── ToolFileRepository.java         # 数据库查询
├── entity/
│   └── ToolFile.java                   # tool_files 表实体
├── model/                              # DTO 模型（6 个类）
├── config/                             # 配置类（2 个类）
└── exception/                          # 异常处理（3 个类）
```

## 配置

当前配置（`src/main/resources/application.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://172.24.0.5:5432/dify
    username: postgres
    password: difyai123456

app:
  dify:
    api-key: dataset-CxGlfh0xHkUoCts6dj17XUhw
    base-url: http://172.24.0.5/v1
  mineru:
    base-url: http://172.24.0.5:8000
    server-type: local
  minio:
    img-path-prefix: http://172.24.0.5:9000/ty-ai-flow
  chunking:
    separator: "\n"
    max-tokens: 1000
    chunk-overlap: 50
```

可通过环境变量覆盖：
```bash
export DIFY_API_KEY=your-key
export MINERU_BASE_URL=http://your-host:8000
```

## 快速开始

### 编译打包
```bash
mvn clean package -DskipTests
```

### 启动服务
```bash
java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

或使用 Maven：
```bash
mvn spring-boot:run
```

### 验证服务
```bash
curl http://localhost:8080/api/dify/document/health
```

预期响应：`OK`

## API 接口

### POST /api/dify/document/ingest

文档入库接口，供 Dify HTTP 插件调用。

**请求体**：
```json
{
  "datasetId": "xxx",
  "fileUrl": "http://xxx/file.pdf",
  "fileName": "file.pdf",
  "fileType": "pdf"
}
```

**响应**：
```json
{
  "success": true,
  "fileIds": ["doc-id-xxx"],
  "stats": {
    "imageCount": 5,
    "chunkCount": 1
  }
}
```

### GET /api/dify/document/health

健康检查接口。

**响应**：`OK`

## 核心实现

### MinerU 图片解析

参考 Dify 官方插件 `src/reference/mineru/tools/parse.py`，通过以下参数启用图片返回：

```java
bodyBuilder.addFormDataPart("return_images", "true");
bodyBuilder.addFormDataPart("return_md", "true");
bodyBuilder.addFormDataPart("return_content_list", "true");
```

### 图片路径替换

1. MinerU 返回 `images` 字段（Map<String, String>，key 为文件名，value 为 base64）
2. 从 PostgreSQL `tool_files` 表查询 `file_key`
3. 拼接真实 MinIO URL：`${imgPathPrefix}/${file_key}`
4. 使用正则表达式精确替换 Markdown 中的图片路径，保留 `![](url)` 语法

```java
// 匹配: ![任意内容](images/xxx.jpg)
String pattern = "(!\\[.*?\\]\\()" + Pattern.quote(tempPath) + "(\\))";
result = result.replaceAll(pattern, "$1" + realUrl + "$2");
```

## 特性

### 优雅降级
- 无数据库配置时，服务仍可正常启动
- 自动跳过图片路径替换，保留 MinerU 原始路径
- 日志会显示：`WARN: 数据库未配置，图片路径替换功能将被禁用`

### 错误处理
- 全局异常处理
- 详细日志记录
- 友好错误响应

### 灵活配置
- 支持环境变量覆盖
- 支持多环境部署
- 数据库可选配置

## 测试

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

### 手动测试

```bash
# 健康检查
curl http://localhost:8080/api/dify/document/health

# 文档入库
curl -X POST http://localhost:8080/api/dify/document/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "your-dataset-id",
    "fileUrl": "http://example.com/file.pdf",
    "fileName": "file.pdf",
    "fileType": "pdf"
  }'
```

## 部署

详见 [DEPLOYMENT.md](DEPLOYMENT.md)

## 监控

### Actuator 端点
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics

### 日志
```bash
tail -f logs/spring.log
```

## 待实现功能

- [ ] Office 文档转 PDF（doc/docx/ppt/pptx）
- [ ] 大文件分片上传
- [ ] 异步处理队列
- [ ] 重试机制
