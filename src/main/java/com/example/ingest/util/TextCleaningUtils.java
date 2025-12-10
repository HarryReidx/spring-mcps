package com.example.ingest.util;

import java.text.Normalizer;

/**
 * 文本清洗工具类
 * 用于修复 MinerU OCR 解析中的 Unicode 康熙部首问题
 */
public class TextCleaningUtils {

    /**
     * 使用 NFKC 标准化清洗文本
     * 将康熙部首 (Kangxi Radicals) 转换为标准 CJK 统一表意文字
     * 
     * @param text 原始文本
     * @return 清洗后的文本
     */
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC);
    }
}
