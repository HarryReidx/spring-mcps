package com.example.ingest.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 请求清理过滤器
 * 用于清理 JSON 中的不可见特殊字符（如 code 160 非断空格）
 */
@Slf4j
@Component
public class RequestCleanupFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // 只处理 POST 请求且 Content-Type 为 JSON 的请求
            if ("POST".equalsIgnoreCase(httpRequest.getMethod()) &&
                httpRequest.getContentType() != null &&
                httpRequest.getContentType().contains("application/json")) {
                
                // 使用包装器读取请求体
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                
                // 继续过滤链
                chain.doFilter(new CleanedRequestWrapper(wrappedRequest), response);
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    /**
     * 清理后的请求包装器
     */
    private static class CleanedRequestWrapper extends ContentCachingRequestWrapper {
        
        public CleanedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            // 读取原始请求体
            byte[] originalBody = super.getContentAsByteArray();
            
            if (originalBody.length > 0) {
                // 转换为字符串
                String bodyStr = new String(originalBody, StandardCharsets.UTF_8);
                
                // 清理特殊字符
                String cleanedBody = cleanBody(bodyStr);
                
                // 如果内容被修改，记录日志
                if (!bodyStr.equals(cleanedBody)) {
                    log.debug("清理了请求体中的特殊字符");
                }
                
                // 返回清理后的输入流
                byte[] cleanedBytes = cleanedBody.getBytes(StandardCharsets.UTF_8);
                return new CleanedServletInputStream(cleanedBytes);
            }
            
            return super.getInputStream();
        }
        
        /**
         * 清理请求体中的特殊字符
         */
        private String cleanBody(String body) {
            // 替换非断空格（code 160）为普通空格（code 32）
            body = body.replace('\u00A0', ' ');
            
            // 替换其他可能的不可见字符
            body = body.replace('\u2009', ' '); // 细空格
            body = body.replace('\u200B', ' '); // 零宽空格
            body = body.replace('\u202F', ' '); // 窄不换行空格
            body = body.replace('\u3000', ' '); // 全角空格
            
            return body;
        }
    }

    /**
     * 清理后的输入流
     */
    private static class CleanedServletInputStream extends ServletInputStream {
        private final byte[] data;
        private int index = 0;

        public CleanedServletInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean isFinished() {
            return index >= data.length;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            if (isFinished()) {
                return -1;
            }
            return data[index++] & 0xFF;
        }
    }
}
