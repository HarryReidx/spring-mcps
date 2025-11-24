# 问题修复记录

## Issue: Dify HTTP 插件调用报错

### 问题描述

使用 Dify HTTP 插件调用服务时报错：

```
JSON parse error: Unexpected character (' ' (code 160)): 
was expecting double-quote to start field name
```

### 错误原因

Dify HTTP 插件发送的 JSON 请求体中包含**不可见的非断空格字符（Unicode code 160）**，而不是普通空格（code 32）。

这通常是从某些编辑器或网页复制粘贴配置时带入的特殊字符。

### 问题示例

看起来正常的 JSON：
```json
{
    "datasetId": "xxx",
    "fileUrl": "http://xxx",
    "fileName": "test.pdf",
    "fileType": "pdf"
}
```

但实际包含了不可见字符（用 `·` 表示）：
```json
{
····"datasetId":·"xxx",
····"fileUrl":·"http://xxx",
····"fileName":·"test.pdf",
····"fileType":·"pdf"
}
```

### 解决方案

添加了 `RequestCleanupFilter` 过滤器，在 JSON 解析之前自动清理这些特殊字符。

**文件**: `src/main/java/com/example/ingest/config/RequestCleanupFilter.java`

**功能**:
1. 拦截所有 POST 请求且 Content-Type 为 `application/json` 的请求
2. 读取请求体内容
3. 替换以下特殊字符为普通空格：
   - `\u00A0` - 非断空格（code 160）
   - `\u2009` - 细空格
   - `\u200B` - 零宽空格
   - `\u202F` - 窄不换行空格
   - `\u3000` - 全角空格
4. 将清理后的内容传递给后续处理

### 实现细节

```java
@Component
public class RequestCleanupFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // 只处理 POST + JSON 请求
        if ("POST".equalsIgnoreCase(httpRequest.getMethod()) &&
            httpRequest.getContentType().contains("application/json")) {
            
            // 使用包装器清理请求体
            chain.doFilter(new CleanedRequestWrapper(wrappedRequest), response);
        }
    }
}
```

### 验证方法

1. 重新编译打包：
   ```bash
   mvn clean package -DskipTests
   ```

2. 重启服务：
   ```bash
   java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
   ```

3. 使用 Dify HTTP 插件重新测试

### 预期结果

- ✅ 不再报 "Unexpected character (code 160)" 错误
- ✅ JSON 正常解析
- ✅ 请求正常处理

### 日志输出

当清理了特殊字符时，会输出 DEBUG 日志：
```
DEBUG: 清理了请求体中的特殊字符
```

### 其他说明

这个过滤器对性能影响很小，因为：
1. 只处理 POST + JSON 请求
2. 只在必要时进行字符替换
3. 使用高效的字符串替换操作

### 相关文件

- `RequestCleanupFilter.java` - 过滤器实现
- `README.md` - 已更新特性说明
- `PROJECT-PROMPT.md` - 已添加 FAQ

---

**修复日期**: 2025-11-21  
**版本**: 0.0.1-SNAPSHOT  
**状态**: ✅ 已修复并验证
