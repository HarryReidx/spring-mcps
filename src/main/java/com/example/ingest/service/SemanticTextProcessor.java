package com.example.ingest.service;

import com.example.ingest.client.VlmClient;
import com.example.ingest.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        
        // 2. 层级结构处理
        if (enableHeaderProcessing) {
            enrichedMarkdown = processHierarchicalStructure(enrichedMarkdown);
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
     * - 空行作为段落分隔符
     * 
     * 示例：
     * # 技术架构
     * 
     * 服务发现模块负责...
     * 
     * 配置中心负责...
     * ↓
     * {{>1#}} 技术架构
     * 
     * {{>2#}} 服务发现模块负责...
     * 
     * {{>2#}} 配置中心负责...
     * 
     * @param markdown 原始 Markdown 文本
     * @return 格式转换后的 Markdown 文本
     */
    private String preprocessForHierarchicalFormat(String markdown) {
        log.debug("开始格式转换（父子分段格式）");
        
        String parentSeparator = appProperties.getProcessRule().getHierarchicalModel().getSeparator();
        String subSeparator = appProperties.getProcessRule().getHierarchicalModel().getSubSeparator();
        
        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        
        boolean inParagraph = false;
        StringBuilder paragraphBuffer = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 检测一级标题
            if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                // 先输出之前的段落
                if (!paragraphBuffer.isEmpty()) {
                    result.append(subSeparator).append(" ").append(paragraphBuffer.toString().trim()).append("\n\n");
                    paragraphBuffer.setLength(0);
                    inParagraph = false;
                }
                
                // 输出父段标题
                String title = trimmed.substring(2).trim();
                result.append(parentSeparator).append(" ").append(title).append("\n\n");
            }
            // 空行：段落分隔符
            else if (trimmed.isEmpty()) {
                if (inParagraph && !paragraphBuffer.isEmpty()) {
                    result.append(subSeparator).append(" ").append(paragraphBuffer.toString().trim()).append("\n\n");
                    paragraphBuffer.setLength(0);
                    inParagraph = false;
                } else if (!inParagraph) {
                    // 连续空行，保持一个
                    result.append("\n");
                }
            }
            // 普通文本行：累积到段落缓冲区
            else {
                if (!inParagraph) {
                    inParagraph = true;
                }
                if (paragraphBuffer.length() > 0) {
                    paragraphBuffer.append("\n");
                }
                paragraphBuffer.append(line);
            }
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
     * VLM 图片增强：提取上下文并调用 VLM 分析
     */
    private String enrichImageDescriptionsWithVlm(String markdown) {
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String imageUrl = matcher.group(2);
            
            // 提取图片周围上下文（前后各30字符）
            int start = matcher.start();
            int end = matcher.end();
            String beforeContext = markdown.substring(Math.max(0, start - 30), start).replace("\n", " ").trim();
            String afterContext = markdown.substring(end, Math.min(markdown.length(), end + 30)).replace("\n", " ").trim();
            String context = String.format("图片前部分文本：%s | 图片后部分文本：%s", beforeContext, afterContext);
            
            // 调用 VLM 分析（设置超时）
            try {
                VlmClient.ImageAnalysisResult analysis = vlmClient.analyzeImageAsync(imageUrl, imageUrl, context)
                        .get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (analysis.isSuccess()) {
                    String enrichedAlt = buildEnrichedAlt(analysis);
                    String replacement = "![" + enrichedAlt + "](" + imageUrl + ")";
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                    log.debug("VLM 增强完成: {}", imageUrl);
                } else {
                    vlmFailedImages.add(imageUrl);
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                }
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("VLM 分析超时: {}", imageUrl);
                vlmFailedImages.add(imageUrl);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } catch (Exception e) {
                log.error("VLM 分析失败: {}", imageUrl, e);
                vlmFailedImages.add(imageUrl);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
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
     * 层级结构深度处理
     * 
     * 三步走策略：
     * 1. 格式转换：将标准 Markdown 转换为 Dify 层级分段格式
     * 2. 上下文注入：为子分段注入父标题上下文
     * 3. 递归预切分：对超长子段进行智能切分
     * 
     * @param markdown 原始 Markdown 文本
     * @return 处理后的 Markdown 文本
     */
    private String processHierarchicalStructure(String markdown) {
        log.debug("开始层级结构深度处理");
        
        // 步骤1：格式转换（# -> {{>1#}}, 段落 -> {{>2#}}）
        String formatted = preprocessForHierarchicalFormat(markdown);
        
        // 步骤2：上下文注入（{{>2#}} 子标题 -> {{>2#}} （所属章节：xxx） 子标题）
        String contextInjected = injectParentContext(formatted);
        
        // 步骤3：递归预切分（防止超长子段被 Dify 截断）
        String chunked = applyRecursivePreChunking(contextInjected);
        
        log.debug("层级结构深度处理完成");
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
        
        String subSeparator = appProperties.getProcessRule().getHierarchicalModel().getSubSeparator();
        
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
        
        String subSeparator = appProperties.getProcessRule().getHierarchicalModel().getSubSeparator();
        int safeSplitThreshold = appProperties.getProcessRule().getHierarchicalModel().getSafeSplitThreshold();
        
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
}
