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
