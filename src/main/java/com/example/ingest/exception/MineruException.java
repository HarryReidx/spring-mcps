package com.example.ingest.exception;

/**
 * MinerU 服务异常
 * 当 MinerU API 调用失败时抛出
 */
public class MineruException extends RuntimeException {
    public MineruException(String message) {
        super(message);
    }
    
    public MineruException(String message, Throwable cause) {
        super(message, cause);
    }
}
