# Bug 修复记录

## 问题描述
**Bug ID**: KANGXI-RADICAL-001  
**严重级别**: P0 (Critical)  
**影响范围**: RAG 召回失败  

MinerU OCR 解析生成的 Markdown 文本中混入 Unicode 康熙部首 (Kangxi Radicals)，导致 Dify Embedding 模型无法正确召回文档。

**示例**:
- 错误字符: `\u2F64` (康熙部首 "用")
- 标准字符: `\u7528` (CJK 统一表意文字 "用")
- 视觉相同，但向量检索失败

---

## 解决方案

### 1. 工具类实现
**文件**: `src/main/java/com/example/ingest/util/TextCleaningUtils.java`

```java
public static String cleanText(String text) {
    if (text == null || text.isEmpty()) {
        return text;
    }
    return Normalizer.normalize(text, Normalizer.Form.NFKC);
}
```

**原理**: 使用 Java 原生 `java.text.Normalizer` 的 NFKC 模式，将兼容字符转换为标准形式。

---

### 2. 集成位置
**文件**: `src/main/java/com/example/ingest/service/DocumentIngestService.java`  
**位置**: MinerU 解析返回后，图片路径替换前

```java
// 5.0 清洗 Markdown 文本（修复康熙部首问题）
mdContent = TextCleaningUtils.cleanText(mdContent);
```

**时机选择理由**:
- ✅ MinerU 解析完成，获得原始 Markdown
- ✅ 在 VLM 增强前清洗，确保后续处理使用标准字符
- ✅ 在存入数据库前清洗，确保持久化数据正确

---

### 3. 单元测试
**文件**: `src/test/java/com/example/ingest/util/TextCleaningUtilsTest.java`

**测试用例**:
- ✅ 康熙部首 `\u2F64` → 标准字符 `\u7528`
- ✅ 混合文本清洗
- ✅ 空值处理

**测试结果**: 全部通过 (3/3)

---

## 验证
```bash
mvn test -Dtest=TextCleaningUtilsTest
```

**输出**:
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 影响评估
- **性能影响**: 可忽略 (NFKC 标准化为 O(n) 操作)
- **兼容性**: 无破坏性变更，仅修复字符编码问题
- **回归风险**: 低 (仅影响文本标准化，不改变业务逻辑)
