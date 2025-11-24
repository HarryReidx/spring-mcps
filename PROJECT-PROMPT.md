
# 项目 Prompt 文档

## 项目目标

开发一个独立的 Spring Boot HTTP 服务，用于绕过 Dify 平台的参数大小限制（例如 Python 节点输出不能超过 80000 字节），实现完整的文档解析与知识库入库流程。

### 核心需求

1. **文档下载**：从 URL 下载用户上传的文件
2. **格式转换**：自动转换为 PDF（必要时）
3. **MinerU 解析**：调用 MinerU 进行全图文解析
4. **图片处理**：处理 Markdown 中的图片地址，从数据库查询真实 MinIO URL
5. **Dify 入库**：调用 Dify API 将文本写入知识库

### 关键技术点

- **复刻 Dify 官方 MinerU 插件能力**：参考 `mineru/tools/parse.py`
- **图片解析逻辑**：MinerU 通过 `return_images=true` 参数返回图片信息
- **图片路径替换**：从 PostgreSQL `tool_files` 表查询 `file_key`，拼接真实 MinIO URL
- **精确替换**：使用正则表达式保留 Markdown 图片语法 `![](url)`

---

## 当前状态

### 已完成功能 ✅

1. **完整的文档处理流程**
   - 文件下载（支持 HTTP/HTTPS）
   - MinerU 解析（图文混合）
   - 图片路径替换（PostgreSQL + MinIO）
   - Dify 知识库入库

2. **核心实现**
   - `DocumentIngestController`: HTTP 接口层
   - `DocumentIngestService`: 核心业务逻辑
   - `MineruClient`: MinerU API 调用
   - `DifyClient`: Dify API 调用
   - `ToolFileRepository`: 数据库查询

3. **关键特性**
   - 优雅降级：无数据库也能运行
   - 错误处理：全局异常捕获
   - 灵活配置：支持环境变量覆盖
   - 精确替换：保留 Markdown 图片语法

4. **测试验证**
   - MinerU 解析成功
   - 图片路径替换成功
   - Dify API 调用成功
   - 端到端流程验证通过

### 技术栈

- **框架**: Spring Boot 3.5.5
- **数据访问**: Spring Data JDBC
- **数据库**: PostgreSQL 12+
- **HTTP 客户端**: OkHttp 4.12.0
- **工具库**: Lombok, Jackson

### 项目结构

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
│   ├── IngestRequest.java
│   ├── IngestResponse.java
│   ├── MineruParseRequest.java
│   ├── MineruParseResponse.java
│   ├── DifyCreateDocumentRequest.java
│   └── DifyCreateDocumentResponse.java
├── config/                             # 配置类（2 个类）
│   ├── AppProperties.java
│   └── ObjectMapperConfig.java
└── exception/                          # 异常处理（3 个类）
    ├── MineruException.java
    ├── DifyException.java
    └── GlobalExceptionHandler.java
```

### 配置信息

**当前环境**（`src/main/resources/application.yml`）：
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

### API 接口

**POST /api/dify/document/ingest**
```json
// 请求
{
  "datasetId": "xxx",
  "fileUrl": "http://xxx/file.pdf",
  "fileName": "file.pdf",
  "fileType": "pdf"
}

// 响应
{
  "success": true,
  "fileIds": ["doc-id-xxx"],
  "stats": {
    "imageCount": 5,
    "chunkCount": 1
  }
}
```

**GET /api/dify/document/health**
- 响应: `OK`

---

## 待办事项

### 高优先级

1. **Office 文档转 PDF**
   - 实现 doc/docx/ppt/pptx → PDF 转换
   - 当前状态：接口已预留，标注 TODO
   - 建议方案：使用 LibreOffice 或 Apache POI

2. **重试机制**
   - MinerU 调用失败重试
   - Dify API 调用失败重试
   - 数据库查询失败重试
   - 建议：使用 Spring Retry

3. **大文件处理优化**
   - 分片上传支持
   - 流式处理
   - 内存优化

### 中优先级

4. **异步处理**
   - 引入消息队列（RabbitMQ/Kafka）
   - 异步文档处理
   - 进度查询接口

5. **监控增强**
   - 添加 Prometheus 指标
   - 自定义业务指标
   - 告警规则

6. **测试完善**
   - 单元测试
   - 集成测试
   - 性能测试

### 低优先级

7. **功能扩展**
   - 支持更多文档格式
   - 批量处理接口
   - 文档预览功能

8. **安全增强**
   - API 认证
   - 请求限流
   - 文件类型校验

---

## 关键代码说明

### 1. 图片路径替换逻辑

**位置**: `DocumentIngestService.java` 的 `replaceImagePaths` 方法

**核心代码**:
```java
// 使用正则表达式精确匹配 Markdown 图片语法: ![alt](images/xxx.jpg)
String pattern = "(!\\[.*?\\]\\()" + Pattern.quote(tempPath) + "(\\))";
result = result.replaceAll(pattern, "$1" + realUrl + "$2");
```

**说明**:
- 匹配 `![任意内容](images/xxx.jpg)` 格式
- 保留感叹号和方括号
- 只替换括号内的 URL

### 2. MinerU 图片解析

**位置**: `MineruClient.java` 的 `parsePdf` 方法

**关键参数**:
```java
bodyBuilder.addFormDataPart("return_images", "true");  // 返回图片
bodyBuilder.addFormDataPart("return_md", "true");      // 返回 Markdown
bodyBuilder.addFormDataPart("return_content_list", "true");
```

**参考**: `src/reference/mineru/tools/parse.py` 的 `_parse_local_v2` 方法

### 3. 优雅降级

**位置**: `DocumentIngestService.java` 构造函数

**逻辑**:
```java
public DocumentIngestService(..., 
    @Autowired(required = false) ToolFileRepository toolFileRepository, ...) {
    this.toolFileRepository = toolFileRepository;
    this.dbEnabled = toolFileRepository != null;
    
    if (!dbEnabled) {
        log.warn("数据库未配置，图片路径替换功能将被禁用");
    }
}
```

---

## 常见问题

### Q1: 如何添加新的文档格式支持？

修改 `DocumentIngestService.java` 的 `convertToPdfIfNeeded` 方法，实现具体的转换逻辑。

### Q2: 如何调整 MinerU 解析参数？

修改 `MineruClient.java` 的 `parsePdf` 方法中的 `bodyBuilder.addFormDataPart` 调用。

### Q3: 如何修改分段规则？

修改 `application.yml` 中的 `app.chunking` 配置，或修改 `DocumentIngestService.java` 的 `buildDifyRequest` 方法。

### Q4: 如何处理解析失败？

当前会抛出 `MineruException`，由 `GlobalExceptionHandler` 统一处理。可以在 `DocumentIngestService` 中添加重试逻辑。

### Q5: Dify HTTP 插件调用报错 "Unexpected character (code 160)"？

**问题**: Dify HTTP 插件发送的 JSON 包含不可见的非断空格字符（code 160）

**解决方案**: 已添加 `RequestCleanupFilter` 自动清理这些特殊字符

**位置**: `src/main/java/com/example/ingest/config/RequestCleanupFilter.java`

**清理的字符**:
- `\u00A0` - 非断空格（code 160）
- `\u2009` - 细空格
- `\u200B` - 零宽空格
- `\u202F` - 窄不换行空格
- `\u3000` - 全角空格

---

## 开发建议

### 添加新功能时

1. 遵循现有的分层架构（Controller → Service → Client）
2. 使用 Lombok 简化代码
3. 添加详细的日志记录
4. 实现优雅的错误处理
5. 更新相关文档

### 修改配置时

1. 优先使用环境变量
2. 提供合理的默认值
3. 在 `AppProperties.java` 中定义配置类
4. 更新 `application.yml` 和 `README.md`

### 调试技巧

1. 设置日志级别为 DEBUG：
   ```yaml
   logging:
     level:
       com.example.ingest: DEBUG
   ```

2. 查看 Actuator 端点：
   - Health: http://localhost:8080/actuator/health
   - Metrics: http://localhost:8080/actuator/metrics

3. 使用测试脚本：
   ```bash
   ./test-api.sh  # Linux/Mac
   test-api.bat   # Windows
   ```

---

## 参考资料

### 内部文档
- `README.md` - 项目说明
- `DEPLOYMENT.md` - 部署指南
- `src/reference/mineru/` - Dify 官方 MinerU 插件源码

### 外部资源
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [MinerU 文档](https://github.com/opendatalab/MinerU)
- [Dify 文档](https://docs.dify.ai/)

---

## 联系信息

- **项目位置**: `D:\1-workspace\1-tsingyun-ws\5-ai\spring-mcps`
- **版本**: 0.0.1-SNAPSHOT
- **最后更新**: 2025-11-21
- **状态**: ✅ 生产就绪
