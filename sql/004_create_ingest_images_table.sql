-- 创建图片存储表
CREATE TABLE IF NOT EXISTS mcp_ingest_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    minio_url VARCHAR(1000) NOT NULL,
    size BIGINT NOT NULL,
    mimetype VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mcp_ingest_images_name ON mcp_ingest_images(name);
CREATE INDEX idx_mcp_ingest_images_created_at ON mcp_ingest_images(created_at);

COMMENT ON TABLE mcp_ingest_images IS '文档入库图片存储表';
COMMENT ON COLUMN mcp_ingest_images.id IS '主键';
COMMENT ON COLUMN mcp_ingest_images.name IS '图片名称';
COMMENT ON COLUMN mcp_ingest_images.file_key IS 'MinIO 文件路径';
COMMENT ON COLUMN mcp_ingest_images.minio_url IS '完整访问 URL';
COMMENT ON COLUMN mcp_ingest_images.size IS '文件大小（字节）';
COMMENT ON COLUMN mcp_ingest_images.mimetype IS 'MIME 类型';
COMMENT ON COLUMN mcp_ingest_images.created_at IS '创建时间';
COMMENT ON COLUMN mcp_ingest_images.updated_at IS '更新时间';
