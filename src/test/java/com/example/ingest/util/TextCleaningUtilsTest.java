package com.example.ingest.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本清洗工具测试
 */
class TextCleaningUtilsTest {

    @Test
    void testKangxiRadicalNormalization() {
        // 康熙部首 "用" (\u2F64) vs 标准 "用" (\u7528)
        String kangxiText = "\u2F64";  // 康熙部首
        String standardText = "\u7528"; // 标准字符
        
        // 清洗前不相等
        assertNotEquals(kangxiText, standardText);
        
        // 清洗后相等
        String cleaned = TextCleaningUtils.cleanText(kangxiText);
        assertEquals(standardText, cleaned);
    }
    
    @Test
    void testMixedText() {
        String mixed = "这是一个测试\u2F64例";
        String expected = "这是一个测试\u7528例";
        
        String cleaned = TextCleaningUtils.cleanText(mixed);
        assertEquals(expected, cleaned);
    }
    
    @Test
    void testNullAndEmpty() {
        assertNull(TextCleaningUtils.cleanText(null));
        assertEquals("", TextCleaningUtils.cleanText(""));
    }
}
