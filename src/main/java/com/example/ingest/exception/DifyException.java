package com.example.ingest.exception;

/**
 * Dify API 异常
 * 当 Dify API 调用失败时抛出
 */
public class DifyException extends RuntimeException {
    public DifyException(String message) {
        super(message);
    }
    
    public DifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
