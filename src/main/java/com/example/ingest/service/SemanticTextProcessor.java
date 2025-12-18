package com.example.ingest.service;

import com.example.ingest.client.LlmClient;
import com.example.ingest.client.VlmClient;
import com.example.ingest.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 语义文本处理器
 * 
 * 核心功能：
 * 1. Markdown 格式转换：将标准 Markdown 转换为 Dify 层级分段格式
 * 2. 上下文注入：为子分段注入父标题上下文，提升 RAG 检索准确率
 * 3. 递归预切分：防止超长子段被 Dify 硬截断，保持文档结构完整性
 * 4. VLM 图片增强：使用视觉语言模型对图片进行语义描述和 OCR 提取
 * 
 * @author HarryReid(黄药师)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticTextProcessor {
    
    private final AppProperties appProperties;
    private final VlmClient vlmClient;
    private final LlmClient llmClient;
    
    // Markdown 标题正则：匹配 # 开头的标题
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    // Markdown 图片正则：匹配 ![alt](url)
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");

    private final List<String> vlmFailedImages = new ArrayList<>();
    
    /**
     * 语义增强主流程
     * 
     * @param markdown 原始 Markdown 文本
     * @param imageUrls 图片 URL 列表（用于 VLM 分析）
     * @param enableVlm 是否启用 VLM 图片分析
     * @param enableHeaderProcessing 是否启用标题处理
     * @return 增强后的 Markdown 文本
     */
    public String enrichMarkdown(String markdown, Map<String, String> imageUrls, boolean enableVlm, boolean enableHeaderProcessing) {
        log.info("开始语义增强处理，原始文本长度: {}, 启用 VLM: {}, 启用标题处理: {}", 
                markdown.length(), enableVlm, enableHeaderProcessing);
        
        vlmFailedImages.clear();
        String enrichedMarkdown = markdown;
        
        // 1. VLM 图片增强（在此处提取上下文并调用 VLM）
        if (enableVlm && imageUrls != null && !imageUrls.isEmpty()) {
            enrichedMarkdown = enrichImageDescriptionsWithVlm(enrichedMarkdown);
        }
        
        // 2. LLM 摘要增强（仅在父子结构模式下生效）
        if (appProperties.getLlm().getEnabled() && enableHeaderProcessing) {
            enrichedMarkdown = enrichParentWithSummary(enrichedMarkdown);
        }
        
        // 3. 父子结构处理
        if (enableHeaderProcessing) {
            enrichedMarkdown = processParentChildStructure(enrichedMarkdown);
        }
        
        log.info("语义增强完成，增强后文本长度: {}, VLM 失败图片数: {}", enrichedMarkdown.length(), vlmFailedImages.size());
        return enrichedMarkdown;
    }
    
    public List<String> getVlmFailedImages() {
        return new ArrayList<>(vlmFailedImages);
    }
    


    /**
     * 格式转换：将标准 Markdown 转换为 Dify 父子分段格式
     * 
     * 转换规则：
     * - # 一级标题 → {{>1#}} 父段
     * - 标题下的段落 → {{>2#}} 子段
     * - 如果标题后有 "> 摘要：xxx"，追加到父段同一行
     * 
     * 示例：
     * # 技术架构
     * > 摘要：本章介绍...
     * 
     * 服务发现模块负责...
     * ↓
     * {{>1#}} 技术架构 摘要：本章介绍...
     * 
     * {{>2#}} 服务发现模块负责...
     * 
     * @param markdown 原始 Markdown 文本
     * @return 格式转换后的 Markdown 文本
     */
    private String preprocessForParentChildFormat(String markdown) {
        log.debug("开始格式转换（父子分段格式）");
        
        String parentSeparator = appProperties.getProcessRule().getParentChild().getParentSeparator();
        String subSeparator = appProperties.getProcessRule().getParentChild().getSubSeparator();
        
        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        
        boolean inParagraph = false;
        String pendingParentLine = null;
        StringBuilder paragraphBuffer = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // 检测一级标题
            if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                // 先输出之前的段落
                if (!paragraphBuffer.isEmpty()) {
                    result.append(subSeparator).append(" ").append(paragraphBuffer.toString().trim()).append("\n\n");
                    paragraphBuffer.setLength(0);
                    inParagraph = false;
                }
                
                // 输出之前待定的父段
                if (pendingParentLine != null) {
                    result.append(pendingParentLine).append("\n\n");
                }
                
                // 构建父段标题（标题中可能已包含 "摘要：xxx"）
                String title = trimmed.substring(2).trim();
                pendingParentLine = parentSeparator + " " + title;
            }
            // 空行：段落分隔符
            else if (trimmed.isEmpty()) {
                if (inParagraph && !paragraphBuffer.isEmpty()) {
                    result.append(subSeparator).append(" ").append(paragraphBuffer.toString().trim()).append("\n\n");
                    paragraphBuffer.setLength(0);
                    inParagraph = false;
                } else if (!inParagraph && pendingParentLine == null) {
                    result.append("\n");
                }
            }
            // 普通文本行：累积到段落缓冲区
            else {
                // 输出待定的父段
                if (pendingParentLine != null) {
                    result.append(pendingParentLine).append("\n\n");
                    pendingParentLine = null;
                }
                
                if (!inParagraph) {
                    inParagraph = true;
                }
                if (paragraphBuffer.length() > 0) {
                    paragraphBuffer.append("\n");
                }
                paragraphBuffer.append(line);
            }
        }
        
        // 输出待定的父段
        if (pendingParentLine != null) {
            result.append(pendingParentLine).append("\n\n");
        }
        
        // 输出最后的段落
        if (paragraphBuffer.length() > 0) {
            result.append(subSeparator).append(" ").append(paragraphBuffer.toString().trim()).append("\n\n");
        }
        
        log.debug("格式转换完成");
        return result.toString();
    }
    

    /**
     * 解析 Markdown 标题结构
     */
    private List<HeaderNode> parseHeaders(String markdown) {
        List<HeaderNode> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(markdown);
        
        while (matcher.find()) {
            int level = matcher.group(1).length(); // # 的数量
            String title = matcher.group(2).trim();
            int position = matcher.start();
            
            headers.add(new HeaderNode(level, title, position));
        }
        
        log.debug("解析到 {} 个标题", headers.size());
        return headers;
    }

    /**
     * 注入标题上下文到正文
     * 策略：在每个段落前添加 [章节: 一级标题 > 二级标题 > 三级标题]
     */
    private String injectHeaderContext(String markdown, List<HeaderNode> headers) {
        if (headers.isEmpty()) {
            return markdown;
        }
        
        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n");
        
        // 维护标题栈
        Deque<HeaderNode> headerStack = new ArrayDeque<>();
        int headerIndex = 0;
        int currentPosition = 0;
        
        for (String line : lines) {
            // 更新标题栈
            while (headerIndex < headers.size() && headers.get(headerIndex).position <= currentPosition) {
                HeaderNode currentHeader = headers.get(headerIndex);
                
                // 弹出更高或同级的标题
                while (!headerStack.isEmpty() && headerStack.peek().level >= currentHeader.level) {
                    headerStack.pop();
                }
                
                headerStack.push(currentHeader);
                headerIndex++;
            }
            
            // 判断是否需要注入上下文
            boolean isHeader = line.trim().startsWith("#");
            boolean isEmpty = line.trim().isEmpty();
            boolean isImage = line.trim().startsWith("!");
            
            // 在非标题、非空行、非图片的正文段落前注入上下文
            if (!isHeader && !isEmpty && !isImage && !headerStack.isEmpty()) {
                String context = buildHeaderContext(headerStack);
                result.append(context).append("\n");
            }
            
            result.append(line).append("\n");
            currentPosition += line.length() + 1;
        }
        
        return result.toString();
    }

    /**
     * 构建标题上下文字符串
     * 例如：[章节: 部署 > 环境准备 > JDK 安装]
     */
    private String buildHeaderContext(Deque<HeaderNode> headerStack) {
        List<String> titles = new ArrayList<>();
        
        // 从栈底到栈顶（从一级到三级）
        Iterator<HeaderNode> iterator = headerStack.descendingIterator();
        while (iterator.hasNext()) {
            titles.add(iterator.next().title);
        }
        
        return "[章节: " + String.join(" > ", titles) + "]";
    }

    /**
     * VLM 图片增强：并发提取上下文并调用 VLM 分析
     */
    private String enrichImageDescriptionsWithVlm(String markdown) {
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        
        // 收集所有图片信息
        List<ImageTask> imageTasks = new ArrayList<>();
        while (matcher.find()) {
            String imageUrl = matcher.group(2);
            int start = matcher.start();
            int end = matcher.end();
            // 提取图片周围上下文（前后各30字符）
            String beforeContext = markdown.substring(Math.max(0, start - 30), start).replace("\n", " ").trim();
            String afterContext = markdown.substring(end, Math.min(markdown.length(), end + 30)).replace("\n", " ").trim();
            String context = String.format("图片前部分文本：%s | 图片后部分文本：%s", beforeContext, afterContext);
            
            imageTasks.add(new ImageTask(imageUrl, context, matcher.group(0), start, end));
        }
        
        if (imageTasks.isEmpty()) {
            return markdown;
        }
        
        log.info("开始并发分析 {} 张图片", imageTasks.size());
        long startTime = System.currentTimeMillis();
        
        // 并发提交所有图片分析任务
        List<CompletableFuture<ImageTaskResult>> futures = imageTasks.stream()
                .map(task -> vlmClient.analyzeImageAsync(task.imageUrl, task.imageUrl, task.context)
                        .thenApply(analysis -> new ImageTaskResult(task, analysis))
                        .exceptionally(e -> {
                            log.error("VLM 分析异常: {}", task.imageUrl, e);
                            return new ImageTaskResult(task, VlmClient.ImageAnalysisResult.builder()
                                    .imageName(task.imageUrl)
                                    .description("图片分析失败")
                                    .ocrText("")
                                    .success(false)
                                    .build());
                        }))
                .toList();
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 收集结果
        Map<String, String> replacements = new HashMap<>();
        for (CompletableFuture<ImageTaskResult> future : futures) {
            try {
                ImageTaskResult result = future.get();
                if (result.analysis.isSuccess()) {
                    String enrichedAlt = buildEnrichedAlt(result.analysis);
                    String replacement = "![" + enrichedAlt + "](" + result.task.imageUrl + ")";
                    replacements.put(result.task.originalMatch, replacement);
                    log.debug("VLM 增强完成: {}", result.task.imageUrl);
                } else {
                    vlmFailedImages.add(result.task.imageUrl);
                }
            } catch (Exception e) {
                log.error("获取 VLM 结果失败", e);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("并发分析完成，成功 {} 张，失败 {} 张，总耗时 {}ms", 
                replacements.size(), vlmFailedImages.size(), duration);
        
        // 批量替换
        String result = markdown;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        return result;
    }
    
    /**
     * 图片任务
     */
    private static class ImageTask {
        String imageUrl;
        String context;
        String originalMatch;
        int start;
        int end;
        
        ImageTask(String imageUrl, String context, String originalMatch, int start, int end) {
            this.imageUrl = imageUrl;
            this.context = context;
            this.originalMatch = originalMatch;
            this.start = start;
            this.end = end;
        }
    }
    
    /**
     * 图片任务结果
     */
    private static class ImageTaskResult {
        ImageTask task;
        VlmClient.ImageAnalysisResult analysis;
        
        ImageTaskResult(ImageTask task, VlmClient.ImageAnalysisResult analysis) {
            this.task = task;
            this.analysis = analysis;
        }
    }



    /**
     * 构建增强的 alt 文本：格式为 "描述 | 文字: OCR内容"
     */
    private String buildEnrichedAlt(VlmClient.ImageAnalysisResult analysis) {
        StringBuilder alt = new StringBuilder();
        
        // 限制描述长度（避免过长导致切分问题）
        if (!analysis.getDescription().isEmpty()) {
            String description = analysis.getDescription();
            // 限制描述在 200 字符以内
            if (description.length() > 200) {
                description = description.substring(0, 200) + "...";
            }
            alt.append(description);
        }
        
        // 限制 OCR 长度并清理噪音
        if (!analysis.getOcrText().isEmpty()) {
            String ocrText = cleanOcrText(analysis.getOcrText());
            if (!ocrText.isEmpty()) {
                if (alt.length() > 0) {
                    alt.append(" | ");
                }
                // 限制 OCR 在 300 字符以内
                if (ocrText.length() > 300) {
                    ocrText = ocrText.substring(0, 300) + "...";
                }
                alt.append("文字: ").append(ocrText);
            }
        }
        
        return alt.toString();
    }

    /**
     * 清理 OCR 文本噪音
     */
    private String cleanOcrText(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return "";
        }
        
        // 移除常见的噪音模式
        String cleaned = ocrText
                // 移除 HTTP 请求日志
                .replaceAll("HTTP/1\\.\\d.*", "")
                // 移除 GIN 日志
                .replaceAll("\\[GIN\\].*", "")
                // 移除 nginx 日志
                .replaceAll("nginx-\\d+-\\d+.*", "")
                // 移除 sandbox 日志
                .replaceAll("sandbox-\\d+-\\d+.*", "")
                // 移除长 token 字符串
                .replaceAll("[A-Za-z0-9_-]{50,}", "")
                // 移除多余空行
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        
        return cleaned;
    }

    /**
     * 基于摘要的父文档检索增强
     * 
     * 策略：直接在标题后追加摘要，使用正则替换
     */
    private String enrichParentWithSummary(String markdown) {
        log.info("开始 LLM 摘要增强处理");
        long startTime = System.currentTimeMillis();
        
        AppProperties.LlmConfig llmConfig = appProperties.getLlm();
        int contentThreshold = llmConfig.getContentThreshold();
        
        // 解析章节结构
        List<ChapterSection> sections = parseChapterSections(markdown);
        log.info("解析到 {} 个章节", sections.size());
        
        if (sections.isEmpty()) {
            return markdown;
        }
        
        // 并发生成摘要
        Map<String, String> summaryMap = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (ChapterSection section : sections) {
            if (section.content.length() >= contentThreshold) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        String fullText = "标题：" + section.title + "\n\n" + section.content.toString() + "/no_think";
                        String summary = llmClient.summarizeText(fullText);
                        summary = cleanThinkTags(summary);
                        
                        if (!summary.isEmpty()) {
                            synchronized (summaryMap) {
                                summaryMap.put(section.title, summary);
                            }
                        }
                    } catch (Exception e) {
                        log.error("LLM 摘要生成失败: {}", section.title, e);
                    }
                }));
            }
        }
        
        // 等待所有摘要完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 使用正则替换：在一级标题同一行追加摘要
        String result = markdown;
        for (Map.Entry<String, String> entry : summaryMap.entrySet()) {
            String title = entry.getKey();
            String summary = entry.getValue();
            
            // 转义特殊字符并匹配 "# title"（行尾）
            String escapedTitle = Pattern.quote(title);
            Pattern pattern = Pattern.compile("(^# " + escapedTitle + ")\\s*$", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(result);
            
            if (matcher.find()) {
                // 直接追加到标题同一行
                result = matcher.replaceFirst("$1 摘要：" + Matcher.quoteReplacement(summary));
                log.debug("成功插入摘要到标题: {}", title);
            } else {
                log.warn("未找到标题进行摘要插入: {}", title);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("LLM 摘要增强完成，成功生成 {} 个摘要，耗时 {}ms", summaryMap.size(), duration);
        
        return result;
    }

    /**
     * 清理 LLM 输出中的 <think> 标签
     */
    private String cleanThinkTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 移除 <think>...</think> 及其内容
        String cleaned = text.replaceAll("<think>.*?</think>", "").trim();
        
        // 移除可能残留的单独标签
        cleaned = cleaned.replaceAll("</?think>", "").trim();
        
        return cleaned;
    }

    /**
     * 解析章节结构（识别一级标题及其正文内容）
     */
    private List<ChapterSection> parseChapterSections(String markdown) {
        List<ChapterSection> sections = new ArrayList<>();
        
        // 使用正则匹配一级标题
        Pattern h1Pattern = Pattern.compile("^# ([^#\\n].*)$", Pattern.MULTILINE);
        Matcher matcher = h1Pattern.matcher(markdown);
        
        List<Integer> headerPositions = new ArrayList<>();
        List<String> headerTitles = new ArrayList<>();
        
        while (matcher.find()) {
            headerPositions.add(matcher.start());
            headerTitles.add(matcher.group(1).trim());
        }
        
        // 提取每个章节的正文内容
        for (int i = 0; i < headerPositions.size(); i++) {
            int startPos = headerPositions.get(i);
            int endPos = (i + 1 < headerPositions.size()) ? headerPositions.get(i + 1) : markdown.length();
            
            String title = headerTitles.get(i);
            String sectionText = markdown.substring(startPos, endPos);
            
            // 提取正文（跳过标题行）
            String[] lines = sectionText.split("\n", -1);
            StringBuilder content = new StringBuilder();
            
            for (int j = 1; j < lines.length; j++) {
                String line = lines[j].trim();
                // 排除空行、图片、代码块
                if (!line.isEmpty() && !line.startsWith("!") && !line.startsWith("```") && !line.startsWith("#")) {
                    if (content.length() > 0) {
                        content.append("\n");
                    }
                    content.append(lines[j]);
                }
            }
            
            sections.add(new ChapterSection(title, startPos, content));
        }
        
        return sections;
    }

    /**
     * 层级结构深度处理
     * 
     * 三步走策略：
     * 1. 格式转换：将标准 Markdown 转换为 Dify 层级分段格式
     * 2. 摘要注入：将摘要追加到 {{>1#}} 父段之后（如果已生成）
     * 3. 上下文注入：为子分段注入父标题上下文
     * 4. 递归预切分：对超长子段进行智能切分
     * 
     * @param markdown 原始 Markdown 文本
     * @return 处理后的 Markdown 文本
     */
    private String processParentChildStructure(String markdown) {
        log.debug("开始父子结构深度处理");
        
        // 步骤1：格式转换（# -> {{>1#}}, 段落 -> {{>2#}}）
        String formatted = preprocessForParentChildFormat(markdown);
        
        // 步骤2：上下文注入（{{>2#}} 子标题 -> {{>2#}} （所属章节：xxx） 子标题）
        String contextInjected = injectParentContext(formatted);
        
        // 步骤3：递归预切分（防止超长子段被 Dify 截断）
        String chunked = applyRecursivePreChunking(contextInjected);
        
        log.debug("父子结构深度处理完成");
        return chunked;
    }
    
    /**
     * 上下文注入：将父标题注入到子标题中
     * 
     * 目的：解决子分段独立向量化后缺少父标题上下文的问题，提升 RAG 检索准确率
     * 
     * 转换规则：
     * {{>1#}} 技术架构
     * {{>2#}} 服务发现
     * ↓
     * {{>1#}} 技术架构
     * {{>2#}} (所属章节: 技术架构) 服务发现
     * 
     * @param markdown 格式转换后的 Markdown 文本
     * @return 注入上下文后的 Markdown 文本
     */
    private String injectParentContext(String markdown) {
        log.debug("开始上下文注入");
        
        String parentSeparator = appProperties.getProcessRule().getParentChild().getParentSeparator();
        String subSeparator = appProperties.getProcessRule().getParentChild().getSubSeparator();
        
        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        String currentParentTitle = null;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 检测父分段标识
            if (trimmed.startsWith(parentSeparator)) {
                // 提取标题（去掉可能的摘要部分）
                String fullTitle = trimmed.substring(parentSeparator.length()).trim();
                // 如果包含 "摘要："，只取摘要之前的部分作为标题
                int summaryIndex = fullTitle.indexOf("摘要：");
                if (summaryIndex > 0) {
                    currentParentTitle = fullTitle.substring(0, summaryIndex).trim();
                } else {
                    currentParentTitle = fullTitle;
                }
                result.append(line).append("\n");
            }
            // 检测子分段标识，注入父标题
            else if (trimmed.startsWith(subSeparator) && currentParentTitle != null) {
                String subTitle = trimmed.substring(subSeparator.length()).trim();
                // 注入格式：{{>2#}} (所属章节: 父标题) 子标题
                result.append(subSeparator).append(" (所属章节: ").append(currentParentTitle).append(") ").append(subTitle).append("\n");
            }
            // 其他行保持原样
            else {
                result.append(line).append("\n");
            }
        }
        
        log.debug("上下文注入完成");
        return result.toString();
    }
    
    /**
     * 递归预切分：扫描并处理超长子分段
     * 
     * 目的：防止子分段内容超过 Dify 的 max_tokens 限制被硬截断，导致剩余部分丢失 {{>2#}} 前缀
     * 
     * 处理逻辑：
     * 1. 逐行扫描，识别 {{>2#}} 子分段标识
     * 2. 收集子分段的完整内容（包括后续非标识行）
     * 3. 调用 recursiveSplit 进行智能切分
     * 4. 父分段和其他层级标识保持原样
     * 
     * @param markdown 注入上下文后的 Markdown 文本
     * @return 预切分后的 Markdown 文本
     */
    private String applyRecursivePreChunking(String markdown) {
        log.debug("开始递归预切分处理");
        
        String subSeparator = appProperties.getProcessRule().getParentChild().getSubSeparator();
        
        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // 检测是否为子分段标识行
            if (line.startsWith(subSeparator)) {
                // 提取子分段内容（去掉前缀）
                String content = line.substring(subSeparator.length()).trim();
                
                // 收集后续非标识行作为子分段的完整内容
                StringBuilder subContent = new StringBuilder(content);
                int j = i + 1;
                while (j < lines.length && !lines[j].startsWith("{{>")) {
                    subContent.append("\n").append(lines[j]);
                    j++;
                }
                
                // 提取父子标题上下文（用于递归切分时保持上下文）
                String contextPrefix = extractContextPrefix(content);
                
                // 递归切分
                String chunked = recursiveSplit(subContent.toString().trim(), contextPrefix);
                result.append(chunked);
                
                // 跳过已处理的行
                i = j - 1;
            } else if (line.startsWith("{{>1#}}") || line.startsWith("{{>3#}}") || 
                       line.startsWith("{{>4#}}") || line.startsWith("{{>5#}}") || line.startsWith("{{>6#}}")) {
                // 父分段或其他层级标识，保持原样
                result.append(line).append("\n");
            } else if (!line.startsWith("{{>")) {
                // 普通文本行，保持原样
                result.append(line).append("\n");
            }
        }
        
        log.debug("递归预切分完成");
        return result.toString();
    }
    
    /**
     * 提取上下文前缀
     * 
     * 从子分段内容中提取 "(所属章节: xxx)" 前缀，用于递归切分时保持上下文一致性
     * 
     * @param content 子分段内容
     * @return 上下文前缀（如 "(所属章节: 技术架构)"），如果不存在则返回空字符串
     */
    private String extractContextPrefix(String content) {
        // 匹配格式：(所属章节: xxx)
        if (content.startsWith("(所属章节: ")) {
            int endIndex = content.indexOf(")", 10);
            if (endIndex > 0 && endIndex < 100) {
                return content.substring(0, endIndex + 1).trim();
            }
        }
        return "";
    }
    
    /**
     * 递归切分算法
     * 
     * 将超长内容切分为多个带 {{>2#}} 前缀的兄弟节点，保持上下文一致性
     * 
     * 算法流程：
     * 1. 基准情况：如果内容长度 <= safeSplitThreshold，直接返回
     * 2. 递归步骤：
     *    - 在安全阈值范围内寻找最佳切分点（优先标点符号）
     *    - 将内容切分为 currentChunk 和 remainingChunk
     *    - 输出 currentChunk 为一个 {{>2#}} 块
     *    - 递归处理 remainingChunk
     * 
     * @param content 待切分内容
     * @param contextPrefix 上下文前缀（如 "(所属章节: 技术架构)"），用于保持切分后的上下文一致性
     * @return 切分后的 Markdown 文本（包含多个 {{>2#}} 块）
     */
    private String recursiveSplit(String content, String contextPrefix) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        String subSeparator = appProperties.getProcessRule().getParentChild().getSubSeparator();
        int safeSplitThreshold = appProperties.getProcessRule().getParentChild().getSafeSplitThreshold();
        
        // 基准情况：长度在安全范围内
        if (content.length() <= safeSplitThreshold) {
            return subSeparator + " " + content + "\n\n";
        }
        
        // 递归步骤：寻找切分点
        int splitPos = findSplitPosition(content, safeSplitThreshold);
        
        // 防止无限递归：如果切分位置无效，强制切分
        if (splitPos <= 0 || splitPos >= content.length()) {
            splitPos = Math.min(safeSplitThreshold, content.length());
        }
        
        String currentChunk = content.substring(0, splitPos).trim();
        String remainingChunk = content.substring(splitPos).trim();
        
        // 防止无限递归：如果剩余部分为空，直接返回
        if (remainingChunk.isEmpty()) {
            return subSeparator + " " + currentChunk + "\n\n";
        }
        
        // 拼接当前块
        StringBuilder result = new StringBuilder();
        result.append(subSeparator).append(" ").append(currentChunk).append("\n\n");
        
        // 递归处理剩余块（不添加前缀，避免字符串越来越长）
        result.append(recursiveSplit(remainingChunk, contextPrefix));
        
        return result.toString();
    }
    
    /**
     * 寻找最佳切分位置
     * 
     * 在 maxPos 范围内向前查找最近的标点符号，优先级：换行符 > 句号 > 分号/逗号
     * 
     * 目的：在语义边界处切分，保持文本可读性和语义完整性
     * 
     * @param content 待切分内容
     * @param maxPos 最大切分位置（安全阈值）
     * @return 切分位置索引
     */
    private int findSplitPosition(String content, int maxPos) {
        if (maxPos >= content.length()) {
            return content.length();
        }
        
        // 从 maxPos 向前查找，优先级：换行符 > 句号 > 分号/逗号
        for (int i = maxPos; i > maxPos - 200 && i > 0; i--) {
            char c = content.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
        }
        
        for (int i = maxPos; i > maxPos - 200 && i > 0; i--) {
            char c = content.charAt(i);
            if (c == '。' || c == '.') {
                return i + 1;
            }
        }
        
        for (int i = maxPos; i > maxPos - 200 && i > 0; i--) {
            char c = content.charAt(i);
            if (c == '；' || c == ';' || c == '，' || c == ',') {
                return i + 1;
            }
        }
        
        // 找不到合适的标点，直接在 maxPos 切分
        return maxPos;
    }

    /**
     * 标题节点
     */
    private static class HeaderNode {
        int level;      // 标题级别（1-6）
        String title;   // 标题文本
        int position;   // 在原文中的位置
        
        HeaderNode(int level, String title, int position) {
            this.level = level;
            this.title = title;
            this.position = position;
        }
    }

    /**
     * 章节结构（用于摘要增强）
     */
    private static class ChapterSection {
        String title;              // 章节标题
        int headerPosition;        // 标题在原文中的位置
        StringBuilder content;     // 章节正文内容
        
        ChapterSection(String title, int headerPosition, StringBuilder content) {
            this.title = title;
            this.headerPosition = headerPosition;
            this.content = content;
        }
    }

    /**
     * 摘要结果
     */
    private static class SummaryResult {
        int headerPosition;        // 标题位置
        String summary;            // 摘要内容
        boolean success;           // 是否成功
        
        SummaryResult(int headerPosition, String summary, boolean success) {
            this.headerPosition = headerPosition;
            this.summary = summary;
            this.success = success;
        }
    }
}
