# MinerU API 测试指南

## 问题分析

当前错误：`422 Unprocessable Entity from POST http://172.24.0.5:8000/file_parse`

这表示 MinerU 接口路径或参数格式不正确。

## 测试步骤

### 1. 测试 MinerU 连接

```bash
curl http://localhost:8080/api/test/mineru
```

这会测试 MinerU 服务是否可访问。

### 2. 批量测试常见配置

```bash
curl -X POST http://localhost:8080/api/test/mineru/batch \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "http://example.com/test.pdf"
  }'
```

这会自动测试以下配置组合：
- `/v1/pdf/parse` + `pdf_file`
- `/v1/pdf/parse` + `file`
- `/api/parse` + `pdf_file`
- `/api/parse` + `file`
- `/parse` + `pdf_file`
- `/parse` + `file`
- `/file_parse` + `pdf_file`
- `/file_parse` + `file`
- `/upload` + `file`
- `/pdf/upload` + `file`

查看日志找到成功的配置（标记为 ✓）。

### 3. 测试单个配置

找到正确的配置后，可以单独测试：

```bash
curl -X POST http://localhost:8080/api/test/mineru/single \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "http://example.com/test.pdf",
    "path": "/v1/pdf/parse",
    "paramName": "pdf_file"
  }'
```

## 修改配置

找到正确的接口配置后，修改 `MineruClient.java`：

1. 修改接口路径（第 38 行）：
```java
String url = baseUrl + "/正确的路径";
```

2. 修改参数名（第 41 行）：
```java
builder.part("正确的参数名", new ByteArrayResource(pdfBytes) {
```

## 常见的 MinerU 接口格式

根据 MinerU 开源项目，常见的接口格式可能是：

1. **标准 REST API**：
   - 路径：`/api/v1/parse` 或 `/v1/pdf/parse`
   - 参数：`file` 或 `pdf_file`

2. **简化版本**：
   - 路径：`/parse` 或 `/upload`
   - 参数：`file`

3. **自定义版本**：
   - 需要查看你的 MinerU 部署文档

## 查看 MinerU 文档

如果有 MinerU 的 Swagger/OpenAPI 文档，可以访问：
- `http://172.24.0.5:8000/docs`
- `http://172.24.0.5:8000/swagger`
- `http://172.24.0.5:8000/api/docs`

## 直接测试 MinerU

也可以直接用 curl 测试 MinerU：

```bash
# 测试根路径
curl http://172.24.0.5:8000/

# 测试文件上传
curl -X POST http://172.24.0.5:8000/v1/pdf/parse \
  -F "pdf_file=@test.pdf"
```

## 更新代码

找到正确配置后，更新 `src/main/java/com/example/difyingest/client/MineruClient.java` 的第 38 和 41 行。
