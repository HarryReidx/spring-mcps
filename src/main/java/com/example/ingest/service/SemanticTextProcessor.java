package com.example.ingest.service;

import com.example.ingest.client.VlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义文本处理器
 * 负责 Markdown 的语义增强和重写
 */
@Slf4j
@Component
public class SemanticTextProcessor {
    
    // Markdown 标题正则：匹配 # 开头的标题
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    // Markdown 图片正则：匹配 ![alt](url)
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");

    /**
     * 语义增强主流程
     * 
     * @param markdown 原始 Markdown
     * @param imageAnalysisResults VLM 图片分析结果（可选）
     * @return 增强后的 Markdown
     */
    public String enrichMarkdown(String markdown, Map<String, VlmClient.ImageAnalysisResult> imageAnalysisResults) {
        log.info("开始语义增强处理，原始文本长度: {}", markdown.length());
        
        // 1. 解析标题结构
        List<HeaderNode> headers = parseHeaders(markdown);
        
        // 2. 注入标题上下文
        String enrichedMarkdown = injectHeaderContext(markdown, headers);
        
        // 3. 增强图片描述（如果启用了 VLM）
        if (imageAnalysisResults != null && !imageAnalysisResults.isEmpty()) {
            enrichedMarkdown = enrichImageDescriptions(enrichedMarkdown, imageAnalysisResults);
        }
        
        log.info("语义增强完成，增强后文本长度: {}", enrichedMarkdown.length());
        return enrichedMarkdown;
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
     * 增强图片描述
     * 将 ![](url) 替换为 ![VLM描述 | OCR内容](url)
     */
    private String enrichImageDescriptions(String markdown, Map<String, VlmClient.ImageAnalysisResult> analysisResults) {
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String originalAlt = matcher.group(1);
            String imageUrl = matcher.group(2);
            
            // 直接使用完整 URL 作为 key 查找分析结果
            VlmClient.ImageAnalysisResult analysis = analysisResults.get(imageUrl);
            
            if (analysis != null && analysis.isSuccess()) {
                // 构建增强的 alt 文本
                String enrichedAlt = buildEnrichedAlt(analysis);
                String replacement = "![" + enrichedAlt + "](" + imageUrl + ")";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                
                log.debug("增强图片描述: {} -> {}", imageUrl, enrichedAlt.substring(0, Math.min(50, enrichedAlt.length())));
            } else {
                // 保持原样
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                log.debug("未找到图片分析结果，保持原样: {}", imageUrl);
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 从 URL 中提取图片名称
     */
    private String extractImageName(String imageUrl) {
        // 从 URL 中提取最后一段作为文件名
        // 例如: http://minio/bucket/image_0.jpg -> image_0.jpg
        int lastSlash = imageUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < imageUrl.length() - 1) {
            return imageUrl.substring(lastSlash + 1);
        }
        return imageUrl;
    }

    /**
     * 构建增强的 alt 文本
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
     * 清理 OCR 文本中的噪音
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
