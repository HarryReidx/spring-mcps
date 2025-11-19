# MinerU 响应格式适配指南

## 当前配置

- **接口路径**: `/file_parse`
- **参数名**: `files`
- **Content-Type**: `multipart/form-data`

## 需要根据实际 MinerU 响应调整的地方

### 1. 响应 DTO (MineruParseResponse.java)

当前假设的响应格式：
```json
{
  "text": "markdown 文本内容，包含 ![](临时URL)",
  "images": [
    {
      "id": "图片ID",
      "previewUrl": "临时图片URL"
    }
  ]
}
```

**如果 MinerU 实际响应格式不同，需要修改：**

`src/main/java/com/example/difyingest/model/MineruParseResponse.java`

常见的可能格式：

#### 格式 1：嵌套在 data 中
```json
{
  "code": 0,
  "data": {
    "markdown": "...",
    "images": [...]
  }
}
```

修改为：
```java
@Data
public class MineruParseResponse {
    private Integer code;
    private Data data;
    
    @Data
    public static class Data {
        private String markdown;  // 或 text
        private List<ImageInfo> images;
    }
    
    @Data
    public static class ImageInfo {
        private String id;
        private String url;  // 或 previewUrl
    }
}
```

#### 格式 2：直接返回 markdown 和 images
```json
{
  "markdown": "...",
  "images": [
    {
      "image_id": "...",
      "url": "..."
    }
  ]
}
```

修改为：
```java
@Data
public class MineruParseResponse {
    private String markdown;
    private List<ImageInfo> images;
    
    @Data
    public static class ImageInfo {
        @JsonProperty("image_id")
        private String id;
        private String url;
    }
}
```

### 2. 调整 Service 中的字段访问

修改 `DocumentIngestService.java` 中的字段访问：

**当前代码（第 50-54 行）：**
```java
MineruParseResponse mineruResponse = mineruClient.parsePdf(pdfBytes, request.getFileName());
if (mineruResponse == null || mineruResponse.getText() == null) {
    throw new RuntimeException("MinerU 返回结果为空");
}
```

**如果响应格式是嵌套的，改为：**
```java
MineruParseResponse mineruResponse = mineruClient.parsePdf(pdfBytes, request.getFileName());
if (mineruResponse == null || mineruResponse.getData() == null || mineruResponse.getData().getMarkdown() == null) {
    throw new RuntimeException("MinerU 返回结果为空");
}
```

**在 replaceImageUrls 方法中（第 109 行）：**
```java
String markdown = mineruResponse.getText();  // 改为 mineruResponse.getData().getMarkdown()
List<MineruParseResponse.ImageInfo> images = mineruResponse.getImages();  // 改为 mineruResponse.getData().getImages()
```

### 3. 测试并查看实际响应

运行服务后，查看日志中的实际响应：

```bash
# 查看日志，找到 MinerU 的响应
tail -f logs/spring.log | grep "MinerU"
```

或者在 `MineruClient.java` 中添加响应日志：

```java
.bodyToMono(String.class)  // 先获取原始字符串
.doOnNext(body -> log.info("MinerU 原始响应: {}", body))  // 打印原始响应
.map(body -> objectMapper.readValue(body, MineruParseResponse.class))  // 再解析
```

## 快速测试步骤

1. 启动服务
2. 调用接口
3. 查看错误日志中的响应体
4. 根据实际响应格式修改 `MineruParseResponse.java`
5. 重新编译运行

## 常见字段名映射

| 可能的字段名 | 对应含义 |
|------------|---------|
| text / markdown / content | Markdown 文本 |
| images / image_list / pictures | 图片列表 |
| id / image_id / img_id | 图片 ID |
| url / preview_url / image_url | 图片 URL |

## 如果 MinerU 返回错误

查看日志中的错误响应体，可能包含：
- 错误码
- 错误信息
- 参数要求说明

根据错误信息调整请求参数。
