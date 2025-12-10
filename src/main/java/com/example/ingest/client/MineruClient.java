package com.example.ingest.client;

import com.example.ingest.config.AppProperties;
import com.example.ingest.exception.MineruException;
import com.example.ingest.model.MineruParseRequest;
import com.example.ingest.model.MineruParseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * MinerU 客户端 - 复刻 Dify 插件的图文解析逻辑
 * 参考: src/reference/mineru/tools/parse.py
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MineruClient {
    
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(1800, TimeUnit.SECONDS)  // 30分钟，解决大文件解析超时
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用 MinerU 解析 PDF（本地 v2 API）
     * 关键：启用 return_images=true 以获取图片信息
     */
    public MineruParseResponse parsePdf(File pdfFile, String originalFileName) {
        log.info("开始调用 MinerU 解析文件: {}", originalFileName);
        
        String url = appProperties.getMineru().getBaseUrl() + "/file_parse";
        
        // 构建请求体（multipart/form-data）
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("files", originalFileName,
                        RequestBody.create(pdfFile, MediaType.parse("application/pdf")));
        
        // 添加解析参数（参考 parse.py 的 _parse_local_v2）
        bodyBuilder.addFormDataPart("parse_method", appProperties.getMineru().getParseMethod());
        bodyBuilder.addFormDataPart("return_md", "true");
        bodyBuilder.addFormDataPart("return_model_output", "false");
        bodyBuilder.addFormDataPart("return_content_list", "true");
        bodyBuilder.addFormDataPart("lang_list", appProperties.getMineru().getLanguage());
        bodyBuilder.addFormDataPart("return_images", "true");  // 关键：返回图片
        bodyBuilder.addFormDataPart("backend", appProperties.getMineru().getBackend());
        bodyBuilder.addFormDataPart("formula_enable", appProperties.getMineru().getEnableFormula().toString());
        bodyBuilder.addFormDataPart("table_enable", appProperties.getMineru().getEnableTable().toString());
        bodyBuilder.addFormDataPart("return_middle_json", "false");
        
        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .addHeader("accept", "application/json")
                .build();
        
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("MinerU 解析失败: status={}, body={}", response.code(), errorBody);
                throw new MineruException("MinerU 解析失败: " + errorBody);
            }
            
            String responseBody = response.body().string();
            log.debug("MinerU 响应: {}", responseBody);
            
            MineruParseResponse parseResponse = objectMapper.readValue(responseBody, MineruParseResponse.class);
            log.info("MinerU 解析成功，返回 {} 个文件结果", parseResponse.getResults().size());
            
            return parseResponse;
            
        } catch (IOException e) {
            log.error("调用 MinerU 失败", e);
            throw new MineruException("调用 MinerU 失败: " + e.getMessage(), e);
        }
    }
}
