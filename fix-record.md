# 修复记录：Dify 层级分段优化

## 修改文件
`src/main/java/com/example/ingest/service/SemanticTextProcessor.java`

---

## 问题背景

### 痛点1：上下文丢失 (Context Loss)
子分段 `{{>2#}}` 是独立的向量块，缺少父分段 `{{>1#}}` 的背景信息，导致检索准确率低。

**示例**：
```
{{>1#}} 技术架构
{{>2#}} 服务发现
```
用户搜索"技术架构的服务发现"时，子分段因缺少"技术架构"上下文而召回失败。

### 痛点2：硬截断 (Hard Cut)
子分段内容超过 `max_tokens`（2048）时，Dify 强制截断，剩余部分丢失 `{{>2#}}` 前缀，破坏文档结构。

---

## 解决方案

### 核心方法：`processHierarchicalStructure(String markdown)`

执行三个步骤：
1. **格式转换**：`#` 标题 → `{{>1#}}`，段落 → `{{>2#}}`
2. **上下文注入**：`{{>2#}} 子标题` → `{{>2#}} 父标题 - 子标题`
3. **递归预切分**：超长子段智能切分，保持上下文一致性

---

## 代码实现

### 1. 主流程重构

```java
public String enrichMarkdown(String markdown, Map<String, VlmClient.ImageAnalysisResult> imageAnalysisResults, boolean enableHeaderProcessing) {
    log.info("开始语义增强处理，原始文本长度: {}, 启用标题处理: {}", markdown.length(), enableHeaderProcessing);
    
    String enrichedMarkdown = markdown;
    
    // 1. 如果启用标题处理（AUTO 模式 + 父子分段），才进行标题解析和上下文注入
    if (enableHeaderProcessing) {
        List<HeaderNode> headers = parseHeaders(markdown);
        enrichedMarkdown = injectHeaderContext(markdown, headers);
    }
    
    // 2. 增强图片描述（如果启用了 VLM）
    if (imageAnalysisResults != null && !imageAnalysisResults.isEmpty()) {
        enrichedMarkdown = enrichImageDescriptions(enrichedMarkdown, imageAnalysisResults);
    }
    
    // 3. 层级结构深度处理（格式转换 + 上下文注入 + 递归预切分）
    if (enableHeaderProcessing) {
        enrichedMarkdown = processHierarchicalStructure(enrichedMarkdown);
    }
    
    log.info("语义增强完成，增强后文本长度: {}", enrichedMarkdown.length());
    return enrichedMarkdown;
}
```

### 2. 层级结构深度处理

```java
/**
 * 层级结构深度处理：格式转换 + 上下文注入 + 递归预切分
 */
private String processHierarchicalStructure(String markdown) {
    log.debug("开始层级结构深度处理");
    
    // 步骤1：格式转换（# -> {{>1#}}, 段落 -> {{>2#}}）
    String formatted = preprocessForHierarchicalFormat(markdown);
    
    // 步骤2：上下文注入（{{>2#}} 子标题 -> {{>2#}} 父标题 - 子标题）
    String contextInjected = injectParentContext(formatted);
    
    // 步骤3：递归预切分（防止超长子段被 Dify 截断）
    String chunked = applyRecursivePreChunking(contextInjected);
    
    log.debug("层级结构深度处理完成");
    return chunked;
}
```

### 3. 上下文注入

```java
/**
 * 上下文注入：将父标题注入到子标题中
 * {{>1#}} 技术架构 -> {{>2#}} 服务发现 => {{>2#}} 技术架构 - 服务发现
 */
private String injectParentContext(String markdown) {
    log.debug("开始上下文注入");
    
    String parentSeparator = appProperties.getProcessRule().getHierarchicalModel().getSeparator();
    String subSeparator = appProperties.getProcessRule().getHierarchicalModel().getSubSeparator();
    
    StringBuilder result = new StringBuilder();
    String[] lines = markdown.split("\n", -1);
    String currentParentTitle = null;
    
    for (String line : lines) {
        String trimmed = line.trim();
        
        // 检测父分段标识
        if (trimmed.startsWith(parentSeparator)) {
            currentParentTitle = trimmed.substring(parentSeparator.length()).trim();
            result.append(line).append("\n");
        }
        // 检测子分段标识，注入父标题
        else if (trimmed.startsWith(subSeparator) && currentParentTitle != null) {
            String subTitle = trimmed.substring(subSeparator.length()).trim();
            // 注入格式：{{>2#}} 父标题 - 子标题
            result.append(subSeparator).append(" ").append(currentParentTitle).append(" - ").append(subTitle).append("\n");
        }
        // 其他行保持原样
        else {
            result.append(line).append("\n");
        }
    }
    
    log.debug("上下文注入完成");
    return result.toString();
}
```

### 4. 递归预切分（带上下文保持）

```java
/**
 * 递归切分算法：将超长内容切分为多个带 {{>2#}} 前缀的兄弟节点
 * @param content 待切分内容
 * @param contextPrefix 父标题上下文（用于保持切分后的上下文一致性）
 */
private String recursiveSplit(String content, String contextPrefix) {
    if (content == null || content.isEmpty()) {
        return "";
    }
    
    String subSeparator = appProperties.getProcessRule().getHierarchicalModel().getSubSeparator();
    int safeSplitThreshold = appProperties.getProcessRule().getHierarchicalModel().getSafeSplitThreshold();
    
    // 基准情况：长度在安全范围内
    if (content.length() <= safeSplitThreshold) {
        return subSeparator + " " + content + "\n\n";
    }
    
    // 递归步骤：寻找切分点
    int splitPos = findSplitPosition(content, safeSplitThreshold);
    
    String currentChunk = content.substring(0, splitPos).trim();
    String remainingChunk = content.substring(splitPos).trim();
    
    // 拼接当前块 + 递归处理剩余块
    StringBuilder result = new StringBuilder();
    result.append(subSeparator).append(" ").append(currentChunk).append("\n\n");
    
    // 关键：剩余部分也要保持父标题上下文
    if (!contextPrefix.isEmpty() && !remainingChunk.startsWith(contextPrefix)) {
        remainingChunk = contextPrefix + " - (续) " + remainingChunk;
    }
    
    result.append(recursiveSplit(remainingChunk, contextPrefix));
    
    return result.toString();
}
```

### 5. 上下文前缀提取

```java
/**
 * 提取上下文前缀（父标题 - 子标题）
 */
private String extractContextPrefix(String content) {
    // 如果内容以 "父标题 - 子标题" 格式开头，提取它
    int dashIndex = content.indexOf(" - ");
    if (dashIndex > 0 && dashIndex < 100) {
        return content.substring(0, dashIndex).trim();
    }
    return "";
}
```

---

## 效果示例

### 输入（原始 Markdown）
```markdown
# 技术架构

服务发现模块负责...（超过1800字符的长文本）

# 部署指南

Docker 部署步骤...
```

### 输出（处理后）
```
{{>1#}} 技术架构

{{>2#}} 技术架构 - 服务发现模块负责...（前1800字符）

{{>2#}} 技术架构 - (续) ...（剩余部分，保持上下文）

{{>1#}} 部署指南

{{>2#}} 部署指南 - Docker 部署步骤...
```

---

## 关键改进

1. **上下文注入**：每个子分段都包含父标题，提升检索准确率
2. **智能切分**：超长子段在标点符号处切分，保持语义完整性
3. **上下文保持**：切分后的剩余部分自动添加 `(续)` 标记，保持上下文一致性
4. **配置化**：`safe-split-threshold` 可通过 YAML 配置调整

---

## 配置参数

`src/main/resources/application.yml`：
```yaml
process-rule:
  hierarchical-model:
    separator: "{{>1#}}"
    sub-separator: "{{>2#}}"
    safe-split-threshold: 1800
```

---

# 功能新增：文件大小记录与 VLM 失败追踪

**日期**: 2025-12-11

**新增功能**:
1. 记录原始文件大小到任务表
2. 追踪 VLM 分析失败的图片 URL
3. 前端任务列表显示文件大小列
4. VLM 分析超时优化（30秒）
5. 前端耗时列格式化（毫秒转秒）

### 数据库变更：`sql/003_add_file_size_and_vlm_errors.sql`

```sql
ALTER TABLE mcp_ingest_tasks 
ADD COLUMN file_size BIGINT,
ADD COLUMN vlm_failed_images TEXT;
```

### 后端修改

**`src/main/java/com/example/ingest/entity/IngestTask.java`**
```java
@Column("file_size")
private Long fileSize;

@Column("vlm_failed_images")
private String vlmFailedImages;  // JSON 数组
```

**`src/main/java/com/example/ingest/service/SemanticTextProcessor.java`**
- 添加 `vlmFailedImages` 列表收集失败图片
- 在 VLM 分析失败、超时或返回失败时记录图片 URL
- 提供 `getVlmFailedImages()` 方法供外部获取

**`src/main/java/com/example/ingest/service/DocumentIngestService.java`**
- 在语义增强后调用 `semanticTextProcessor.getVlmFailedImages()` 获取失败图片
- 将失败图片列表传递到 `IngestResponse`

**`src/main/java/com/example/ingest/service/IngestTaskService.java`**
- 在 `updateTaskSuccess` 中保存 VLM 失败图片列表到数据库

### 前端修改

**`frontend/src/views/TaskList.vue`**
- 添加文件大小列，使用 `formatFileSize` 格式化
- 修改耗时列格式化：毫秒转秒（< 1s 显示 0.xx s，>= 1s 显示 x.x s）
- 分离 `formatTime` (耗时) 和 `formatDateTime` (时间戳)

**`frontend/src/views/TaskDetail.vue`**
- 修改耗时显示格式化：使用 `formatDuration` 方法
- 统一耗时格式：毫秒转秒

**影响**: 
- 任务监控页面可查看文件大小和 VLM 失败图片数
- 耗时显示更友好（秒为单位）
- 便于分析大文件处理性能和 VLM 失败原因

---

# 优化记录：超时调整与 VLM 上下文增强

**日期**: 2025-12-05

## 1. 超时配置调整

### 修改文件：`src/main/java/com/example/ingest/client/MineruClient.java`

**问题**: 处理 184MB PDF 时，在 300秒（5分钟）处发生 SocketTimeoutException

**解决方案**: 将 readTimeout 从 300秒 增加到 1800秒（30分钟），writeTimeout 调整为 300秒

```java
private OkHttpClient getHttpClient() {
    return new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(1800, TimeUnit.SECONDS)  // 30分钟，解决大文件解析超时
            .writeTimeout(300, TimeUnit.SECONDS)
            .build();
}
```

### 修改文件：`src/main/java/com/example/ingest/service/DocumentIngestService.java`

**问题**: 大文件下载过程可能超过 5 分钟

**解决方案**: 将 httpClient 的 readTimeout 从 300秒 增加到 1800秒（30分钟）

```java
private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(1800, TimeUnit.SECONDS)  // 30分钟，解决大文件下载超时
        .build();
```

## 2. VLM 客户端上下文支持

### 修改文件：`src/main/java/com/example/ingest/client/VlmClient.java`

**问题**: VLM 仅接收图片 URL，缺乏周围文字信息，导致描述不够准确

**解决方案**: 
1. 修改 `analyzeImageAsync` 和 `analyzeImage` 方法签名，增加 `String context` 参数
2. 在 `analyzeImage` 方法中拼接上下文到 Prompt

```java
// 方法签名升级
public CompletableFuture<ImageAnalysisResult> analyzeImageAsync(String imageUrl, String imageName, String context) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            return analyzeImage(imageUrl, imageName, context);
        } catch (Exception e) {
            log.error("VLM 分析图片失败: {}", imageName, e);
            return ImageAnalysisResult.builder()
                    .imageName(imageName)
                    .description("图片分析失败")
                    .ocrText("")
                    .success(false)
                    .build();
        }
    });
}

private ImageAnalysisResult analyzeImage(String imageUrl, String imageName, String context) throws IOException {
    // ... 省略部分代码 ...
    
    // 拼接上下文到 Prompt
    String configPrompt = vlmConfig.getPrompt();
    String finalPrompt = String.format("结合图片周围的上下文文本：【%s】，%s", context, configPrompt);
    
    // 构建请求体
    String requestBody = buildVisionRequest(imageData, finalPrompt, provider);
    
    // ... 省略部分代码 ...
}
```

## 3. Markdown 上下文提取逻辑

### 修改文件：`src/main/java/com/example/ingest/service/SemanticTextProcessor.java`

**问题**: 图片分析缺少周围文本上下文

**解决方案**: 在 `enrichImageDescriptions` 方法中，提取图片前后各 20 字符作为上下文

```java
private String enrichImageDescriptions(String markdown, Map<String, VlmClient.ImageAnalysisResult> analysisResults) {
    Matcher matcher = IMAGE_PATTERN.matcher(markdown);
    StringBuffer result = new StringBuffer();
    
    while (matcher.find()) {
        String originalAlt = matcher.group(1);
        String imageUrl = matcher.group(2);
        
        // 提取图片周围的上下文（前后各20字符）
        int start = matcher.start();
        int end = matcher.end();
        int len = markdown.length();
        
        String beforeContext = markdown.substring(Math.max(0, start - 20), start);
        String afterContext = markdown.substring(end, Math.min(len, end + 20));
        
        // 清洗上下文：替换换行符为空格
        String context = (beforeContext + " " + afterContext).replace("\n", " ").trim();
        
        // ... 省略部分代码 ...
    }
    
    matcher.appendTail(result);
    return result.toString();
}
```

### 修改文件：`src/main/java/com/example/ingest/service/DocumentIngestService.java`

**调整**: 在 `analyzeImagesWithVlm` 方法中，调用 VLM 时传入空上下文（实际上下文在 `enrichImageDescriptions` 中提取）

```java
private Map<String, VlmClient.ImageAnalysisResult> analyzeImagesWithVlm(Map<String, String> images) {
    // ... 省略部分代码 ...
    
    for (Map.Entry<String, String> entry : imageUrls.entrySet()) {
        String imageName = entry.getKey();
        String imageUrl = entry.getValue();
        // 传入空上下文，实际上下文在 enrichImageDescriptions 中提取
        futures.add(vlmClient.analyzeImageAsync(imageUrl, imageUrl, ""));
    }
    
    // ... 省略部分代码 ...
}
```

## 总结

本次优化解决了两个核心问题：
1. **超时问题**: 将 MinerU 解析和文件下载的超时时间从 5 分钟延长到 30 分钟，支持大文件处理
2. **VLM 上下文缺失**: 为 VLM 图片分析增加周围文本上下文（前后各 20 字符），提升图片描述准确性


---

# 重构：图片存储独立表

**日期**: 2025-12-09

**问题**: 图片记录与 Dify 的 `tool_files` 表耦合，导致字段冲突和维护困难

**解决方案**: 创建独立的 `mcp_ingest_images` 表，包含创建时间和更新时间字段

### 新增文件：`sql/004_create_ingest_images_table.sql`

```sql
CREATE TABLE IF NOT EXISTS mcp_ingest_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    minio_url VARCHAR(1000) NOT NULL,
    size BIGINT NOT NULL,
    mimetype VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 修改文件：
- `src/main/java/com/example/ingest/entity/IngestImage.java` - 新增实体
- `src/main/java/com/example/ingest/repository/IngestImageRepository.java` - 新增仓储
- `src/main/java/com/example/ingest/service/DocumentIngestService.java` - 替换 `ToolFileRepository` 为 `IngestImageRepository`
- `src/main/java/com/example/ingest/service/MinioService.java` - 使用新表保存图片记录

**影响**: 图片存储与 Dify 解耦，支持独立维护和扩展


---

# Bug 修复：必填参数校验

**日期**: 2025-12-09

**问题**: fileName 等必填参数未传时，直接抛出数据库约束异常，错误信息不友好

**解决方案**: 在 Controller 层添加参数校验，返回 400 Bad Request 和明确的错误信息

### 修改文件：`src/main/java/com/example/ingest/controller/DocumentIngestController.java`

```java
private void validateRequest(IngestRequest request) {
    if (request.getDatasetId() == null || request.getDatasetId().isEmpty()) {
        throw new IllegalArgumentException("datasetId 不能为空");
    }
    if (request.getFileUrl() == null || request.getFileUrl().isEmpty()) {
        throw new IllegalArgumentException("fileUrl 不能为空");
    }
    if (request.getFileName() == null || request.getFileName().isEmpty()) {
        throw new IllegalArgumentException("fileName 不能为空");
    }
    if (request.getFileType() == null || request.getFileType().isEmpty()) {
        throw new IllegalArgumentException("fileType 不能为空");
    }
}
```

**影响**: 参数缺失时返回 HTTP 400 和明确错误信息，而非 HTTP 500 数据库异常


---

# 功能新增：MinIO 图片上传

**日期**: 2025-12-09

**问题**: MinerU 返回 base64 图片但未上传到 MinIO，导致大文档图片丢失

**解决方案**: 添加 MinIO 客户端，当数据库无图片记录时自动上传

### 新增文件：`src/main/java/com/example/ingest/service/MinioService.java`

```java
public String uploadImage(String imageName, String base64Data) {
    byte[] imageBytes = Base64.getDecoder().decode(base64Data);
    String fileKey = "images/" + UUID.randomUUID() + "_" + imageName;
    
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(bucketName)
            .object(fileKey)
            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
            .contentType(getContentType(imageName))
            .build()
    );
    
    // 保存到 tool_files 表
    toolFileRepository.save(toolFile);
    return fileKey;
}
```

### 修改文件：`src/main/resources/application.yml`

```yaml
minio:
  endpoint: ${S3_ENDPOINT:http://172.24.0.5:9000}
  access-key: ${S3_ACCESS_KEY:minioadmin}
  secret-key: ${S3_SECRET_KEY:minioadmin}
  bucket-name: ${S3_BUCKET_NAME:ty-ai-flow}
  region: ${S3_REGION:us-east-1}
```

### 修改文件：`pom.xml`

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>
```

**影响**: 大文档图片自动上传到 MinIO 并写入数据库，解决图片丢失问题
