@echo off
echo === 测试健康检查 ===
curl -X GET http://localhost:8080/api/dify/document/health
echo.

echo === 测试文档入库接口（示例）===
curl -X POST http://localhost:8080/api/dify/document/ingest ^
  -H "Content-Type: application/json" ^
  -d "{\"datasetId\":\"your-dataset-id\",\"fileUrl\":\"http://example.com/test.pdf\",\"fileName\":\"test.pdf\",\"fileType\":\"pdf\"}"
echo.
