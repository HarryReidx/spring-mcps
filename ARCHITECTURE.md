# 语义增强 RAG 架构说明

## 核心组件

### 1. VlmClient (视觉理解客户端)

**职责**：调用视觉语言模型（GPT-4o/Claude-3.5）分析图片

**关键方法**：
- `analyzeImageAsync()`: 异步分析单张图片
- `analyzeImage()`: 同步分析图片
- `buildVisionRequest()`: 构建 Vision API 请求

**特性**：
- 支持并发调用，使用 `CompletableFuture`
- 返回图片描述 + OCR 文字
- 失败时返回降级结果，不影响主流程

### 2. SemanticTextProcessor (语义文本处理器)

**职责**：对 Markdown 进行语义增强和重写

**核心策略**：

#### 2.1 标题上下文注入
- 解析 Markdown 标题结构（支持 1-6 级标题）
- 维护标题栈，追踪当前段落所属的标题路径
- 在正文段落前注入标题上下文：`[章节: 一级 > 二级 > 三级]`

#### 2.2 图片描述增强
- 将 `![](url)` 替换为 `![VLM描述 | OCR内容](url)`
- 保留原始 URL，确保图片可访问
- 失败时保持原样，不影响文档完整性

**关键方法**：
- `enrichMarkdown()`: 主入口
- `parseHeaders()`: 解析标题结构
- `injectHeaderContext()`: 注入标题上下文
- `enrichImageDescriptions()`: 增强图片描述

### 3. DocumentIngestService (文档入库服务)

**重构后的流程**：

```
1. 下载文件
   ↓
2. 格式转换 (如需要)
   ↓
3. MinerU 解析 (PDF → Markdown + 图片)
   ↓
4. 替换图片路径 (临时路径 → MinIO URL)
   ↓
5. 语义增强处理
   ├─ [可选] VLM 并发分析图片
   ├─ 解析标题结构
   ├─ 注入标题上下文
   └─ 增强图片描述
   ↓
6. 构建 Dify 请求 (动态 process_rule)
   ↓
7. 调用 Dify create-by-text API
   ↓
8. 清理临时文件
   ↓
9. 返回结果
```

**新增方法**：
- `performSemanticEnrichment()`: 执行语义增强
- `analyzeImagesWithVlm()`: 并发调用 VLM 分析图片
- `getImageRealUrls()`: 获取图片真实 URL
- `buildDifyRequest()`: 动态构建 Dify 请求（支持 AUTO/CUSTOM 模式）

## 数据流

### 输入 (IngestRequest)

```java
{
  "datasetId": "xxx",
  "fileUrl": "http://...",
  "fileName": "doc.pdf",
  "fileType": "pdf",
  
  // RAG 配置
  "chunkingMode": "CUSTOM",      // AUTO | CUSTOM
  "maxTokens": 800,
  "chunkOverlap": 100,
  "separator": "\\n\\n",
  "enableVlm": true,             // 是否启用 VLM
  "indexingTechnique": "high_quality",
  "docForm": "text_model"
}
```

### 中间处理

#### MinerU 输出
```
Markdown: "# 标题\n内容...\n![](images/img_0.jpg)"
Images: {"image_0.jpg": "base64..."}
```

#### 图片路径替换后
```
Markdown: "# 标题\n内容...\n![](http://minio/bucket/abc123.jpg)"
```

#### VLM 分析结果
```java
{
  "image_0.jpg": {
    "description": "这是一张架构图，展示了系统的三层结构",
    "ocrText": "应用层 | 服务层 | 数据层",
    "success": true
  }
}
```

#### 语义增强后
```
# 标题

[章节: 标题]
内容...

![这是一张架构图，展示了系统的三层结构 | OCR: 应用层 | 服务层 | 数据层](http://minio/bucket/abc123.jpg)
```

### 输出 (DifyCreateDocumentRequest)

```java
{
  "name": "doc.pdf",
  "text": "[增强后的完整 Markdown]",
  "indexing_technique": "high_quality",
  "doc_form": "text_model",
  "process_rule": {
    "mode": "custom",
    "rules": {
      "pre_processing_rules": [...],
      "segmentation": {
        "separator": "\\n\\n",
        "max_tokens": 800,
        "chunk_overlap": 100
      }
    }
  }
}
```

## 性能优化

### 1. VLM 并发调用
```java
// 为每张图片创建异步任务
List<CompletableFuture<ImageAnalysisResult>> futures = new ArrayList<>();
for (String imageUrl : imageUrls) {
    futures.add(vlmClient.analyzeImageAsync(imageUrl, imageName));
}

// 等待所有任务完成
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

**优势**：
- 10 张图片串行需要 10 * 5s = 50s
- 并发调用仅需 ~5s

### 2. 轻量级 Markdown 解析
使用正则表达式而非 AST 库：
```java
Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
```

**优势**：
- 无需引入额外依赖
- 解析速度快
- 满足当前需求

### 3. 可选功能
- VLM 分析是可选的（`enableVlm` 参数）
- 数据库查询是可选的（自动检测配置）
- 失败时降级处理，不影响主流程

## 配置管理

### AppProperties 结构
```
app:
  ├─ dify: Dify API 配置
  ├─ mineru: MinerU 服务配置
  ├─ minio: MinIO 存储配置
  ├─ chunking: 默认分块配置
  └─ vlm: VLM 视觉模型配置
```

### 动态配置优先级
1. 请求参数（最高优先级）
2. 配置文件默认值
3. 硬编码默认值

## 扩展性

### 支持更多 VLM 提供商
只需修改 `VlmClient.buildVisionRequest()` 方法，适配不同的 API 格式：
- OpenAI GPT-4o
- Anthropic Claude-3.5
- Google Gemini Vision
- 本地部署的开源模型

### 支持更多语义增强策略
在 `SemanticTextProcessor` 中添加新方法：
- 实体识别和链接
- 关键词提取和注入
- 摘要生成
- 多语言翻译

### 支持更多文档格式
在 `DocumentIngestService.convertToPdfIfNeeded()` 中集成：
- LibreOffice (doc/docx/ppt/pptx)
- Pandoc (markdown/html)
- 其他转换工具
