-- 添加文件下载耗时字段
ALTER TABLE mcp_ingest_tasks ADD COLUMN IF NOT EXISTS download_cost_time BIGINT;

COMMENT ON COLUMN mcp_ingest_tasks.download_cost_time IS '文件下载耗时（毫秒）';
