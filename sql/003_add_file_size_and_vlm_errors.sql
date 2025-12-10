-- 添加文件大小和 VLM 失败图片字段
ALTER TABLE ingest_tasks 
ADD COLUMN file_size BIGINT,
ADD COLUMN vlm_failed_images TEXT;

COMMENT ON COLUMN ingest_tasks.file_size IS '原始文件大小（字节）';
COMMENT ON COLUMN ingest_tasks.vlm_failed_images IS 'VLM 分析失败的图片 URL 列表（JSON 数组）';
