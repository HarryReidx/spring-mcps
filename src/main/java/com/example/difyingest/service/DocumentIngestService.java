package com.example.difyingest.service;

import com.example.difyingest.client.DifyClient;
import com.example.difyingest.client.MineruClient;
import com.example.difyingest.config.AppProperties;
import com.example.difyingest.model.*;
import com.example.difyingest.repository.ToolFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档入库核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final WebClient webClient;
    private final MineruClient mineruClient;
    private final DifyClient difyClient;
    private final ToolFileRepository toolFileRepository;
    private final AppProperties appProperties;

    private static final Set<String> OFFICE_TYPES = Set.of("doc", "docx", "ppt", "pptx");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");

    /**
     * 处理文档入库的主流程
     */
    public DocumentIngestResponse ingestDocument(DocumentIngestRequest request) {
        try {
            log.info("开始处理文档入库: fileName={}, fileType={}", request.getFileName(), request.getFileType());

            // 1. 下载文件
            byte[] fileBytes = downloadFile(request.getFileUrl());
            log.info("文件下载完成，大小: {} bytes", fileBytes.length);

            // 2. 转换为 PDF（如果需要）
            byte[] pdfBytes = convertToPdfIfNeeded(fileBytes, request.getFileType(), request.getFileName());

            // 3. 调用 MinerU 解析 PDF
            MineruParseResponse mineruResponse = mineruClient.parsePdf(pdfBytes, request.getFileName());
            if (mineruResponse == null || mineruResponse.getResults() == null || mineruResponse.getResults().isEmpty()) {
                throw new RuntimeException("MinerU 返回结果为空");
            }

            // 获取第一个文档的 markdown 内容
            String markdown = mineruResponse.getResults().values().iterator().next().getMdContent();
            if (markdown == null || markdown.isEmpty()) {
                throw new RuntimeException("MinerU 返回的 markdown 内容为空");
            }
            
            log.info("MinerU 解析完成，markdown 长度: {}", markdown.length());

            // 4. 查询图片真实路径并替换 markdown 中的 URL
            String finalMarkdown = replaceImageUrls(markdown);
            log.info("图片 URL 替换完成");

            // 5. 调用 Dify 知识库接口写入文档
            DifyCreateDocumentRequest difyRequest = buildDifyRequest(request.getFileName(), finalMarkdown);
            DifyCreateDocumentResponse difyResponse = difyClient.createDocumentByText(request.getDatasetId(), difyRequest);

            // 6. 构造返回结果
            return buildSuccessResponse(difyResponse, finalMarkdown);

        } catch (Exception e) {
            log.error("文档入库失败: {}", e.getMessage(), e);
            throw new RuntimeException("文档入库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件
     */
    private byte[] downloadFile(String fileUrl) {
        log.info("开始下载文件: {}", fileUrl);
        try {
            byte[] bytes = webClient.get()
                    .uri(fileUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            
            if (bytes == null || bytes.length == 0) {
                throw new RuntimeException("下载文件失败或文件为空");
            }
            
            // 检查文件头，判断是否为 PDF
            if (bytes.length > 4) {
                String header = new String(bytes, 0, Math.min(10, bytes.length));
                log.info("文件头信息: {}", header);
                if (!header.startsWith("%PDF")) {
                    log.warn("警告：文件不是标准 PDF 格式，文件头: {}", header);
                }
            }
            
            return bytes;
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("下载文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 如果需要，将 Office 文档转换为 PDF
     * TODO: 实现具体的转换逻辑，可选方案：
     * 1. LibreOffice 命令行: soffice --headless --convert-to pdf
     * 2. JODConverter + LibreOffice
     * 3. Apache POI + PDFBox
     */
    private byte[] convertToPdfIfNeeded(byte[] fileBytes, String fileType, String fileName) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            log.info("文件已是 PDF 格式，无需转换");
            return fileBytes;
        }

        if (OFFICE_TYPES.contains(fileType.toLowerCase())) {
            log.info("检测到 Office 文档类型: {}, 需要转换为 PDF", fileType);
            // TODO: 实现转换逻辑
            // 示例：使用 LibreOffice 命令行
            // Process process = Runtime.getRuntime().exec("soffice --headless --convert-to pdf " + inputPath);
            // 或使用 JODConverter
            throw new UnsupportedOperationException("Office 文档转 PDF 功能尚未实现，请实现转换逻辑");
        }

        log.warn("不支持的文件类型: {}, 尝试直接作为 PDF 处理", fileType);
        return fileBytes;
    }

    /**
     * 替换 markdown 中的图片 URL
     * MinerU 返回的图片路径格式：images/xxx.jpg
     * 需要从数据库查询对应的真实 MinIO 路径
     */
    private String replaceImageUrls(String markdown) {
        // 提取 markdown 中的所有图片路径
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        Map<String, String> imagePathMapping = new HashMap<>();
        
        while (matcher.find()) {
            String imagePath = matcher.group(2);  // 例如：images/xxx.jpg
            if (imagePath.startsWith("images/")) {
                // 提取图片文件名（不含扩展名）作为 ID
                String imageFileName = imagePath.substring(imagePath.lastIndexOf('/') + 1);
                String imageId = imageFileName.substring(0, imageFileName.lastIndexOf('.'));
                imagePathMapping.put(imagePath, imageId);
                log.debug("发现图片: {} -> ID: {}", imagePath, imageId);
            }
        }

        if (imagePathMapping.isEmpty()) {
            log.info("没有图片需要处理");
            return markdown;
        }

        // 查询数据库获取图片真实路径
        String[] imageIds = imagePathMapping.values().toArray(new String[0]);
        List<ToolFile> toolFiles = toolFileRepository.findByIdIn(imageIds);
        
        // 构建 ID（不含连字符）-> fileKey 的映射
        Map<String, String> idToFileKey = toolFiles.stream()
                .collect(Collectors.toMap(ToolFile::getIdAsString, ToolFile::getFileKey));

        // 构建临时路径 -> 真实 URL 的映射
        Map<String, String> urlMapping = new HashMap<>();
        String imgPathPrefix = appProperties.getImg().getPathPrefix();
        
        for (Map.Entry<String, String> entry : imagePathMapping.entrySet()) {
            String imagePath = entry.getKey();
            String imageId = entry.getValue();
            String fileKey = idToFileKey.get(imageId);
            
            if (fileKey != null) {
                String realUrl = imgPathPrefix + fileKey;
                urlMapping.put(imagePath, realUrl);
                log.debug("图片 URL 映射: {} -> {}", imagePath, realUrl);
            } else {
                log.warn("未找到图片 ID 对应的 file_key: {}", imageId);
            }
        }

        // 替换 markdown 中的图片 URL
        matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String alt = matcher.group(1);
            String url = matcher.group(2);
            String realUrl = urlMapping.getOrDefault(url, url);
            matcher.appendReplacement(result, "![" + alt + "](" + realUrl + ")");
        }
        matcher.appendTail(result);

        log.info("图片 URL 替换完成，共替换 {} 个图片", urlMapping.size());
        return result.toString();
    }

    /**
     * 构建 Dify 创建文档请求
     */
    private DifyCreateDocumentRequest buildDifyRequest(String fileName, String markdown) {
        AppProperties.Segmentation seg = appProperties.getSegmentation();
        
        return DifyCreateDocumentRequest.builder()
                .name(fileName)
                .text(markdown)
                .indexingTechnique("high_quality")
                .processRule(DifyCreateDocumentRequest.ProcessRule.builder()
                        .mode("custom")
                        .rules(DifyCreateDocumentRequest.Rules.builder()
                                .preProcessingRules(new Object[0])
                                .segmentation(DifyCreateDocumentRequest.Segmentation.builder()
                                        .separator(seg.getSeparator())
                                        .maxTokens(seg.getMaxTokens())
                                        .chunkOverlap(seg.getChunkOverlap())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * 构建成功响应
     */
    private DocumentIngestResponse buildSuccessResponse(DifyCreateDocumentResponse difyResponse, 
                                                        String markdown) {
        List<String> fileIds = new ArrayList<>();
        if (difyResponse.getDocument() != null 
                && difyResponse.getDocument().getDataSourceInfo() != null
                && difyResponse.getDocument().getDataSourceInfo().getUploadFileId() != null) {
            fileIds.add(difyResponse.getDocument().getDataSourceInfo().getUploadFileId());
        }

        // 统计 markdown 中的图片数量
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        int imageCount = 0;
        while (matcher.find()) {
            imageCount++;
        }

        return DocumentIngestResponse.builder()
                .success(true)
                .message("Ingest document success")
                .fileIds(fileIds)
                .stats(DocumentIngestResponse.Stats.builder()
                        .chunkCount(1)  // 简单设为 1，可根据需要扩展
                        .imageCount(imageCount)
                        .build())
                .build();
    }
}
