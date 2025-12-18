-- 添加 MinerU 解析耗时字段
ALTER TABLE mcp_ingest_tasks ADD COLUMN IF NOT EXISTS mineru_cost_time BIGINT;

COMMENT ON COLUMN mcp_ingest_tasks.mineru_cost_time IS 'MinerU 解析耗时（毫秒）';
