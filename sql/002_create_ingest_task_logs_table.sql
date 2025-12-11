-- ----------------------------
-- Table structure for mcp_ingest_task_logs
-- ----------------------------
DROP TABLE IF EXISTS "public"."mcp_ingest_task_logs";
CREATE TABLE "public"."mcp_ingest_task_logs" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "task_id" uuid NOT NULL,
    "log_level" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'INFO'::character varying,
    "log_message" text COLLATE "pg_catalog"."default" NOT NULL,
    "log_detail" text COLLATE "pg_catalog"."default",
    "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN "public"."mcp_ingest_task_logs"."id" IS '日志唯一标识';
COMMENT ON COLUMN "public"."mcp_ingest_task_logs"."task_id" IS '关联的任务 ID';
COMMENT ON COLUMN "public"."mcp_ingest_task_logs"."log_level" IS '日志级别：INFO, WARN, ERROR';
COMMENT ON COLUMN "public"."mcp_ingest_task_logs"."log_message" IS '日志消息';
COMMENT ON COLUMN "public"."mcp_ingest_task_logs"."log_detail" IS '详细信息（JSON 或文本）';
COMMENT ON TABLE "public"."mcp_ingest_task_logs" IS '任务执行日志表';

-- ----------------------------
-- Indexes structure for table mcp_ingest_task_logs
-- ----------------------------
CREATE INDEX "idx_mcp_ingest_task_logs_task_id" ON "public"."mcp_ingest_task_logs" USING btree (
    "task_id" "pg_catalog"."uuid_ops" ASC NULLS LAST
);
CREATE INDEX "idx_mcp_ingest_task_logs_created_at" ON "public"."mcp_ingest_task_logs" USING btree (
    "created_at" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
);
CREATE INDEX "idx_mcp_ingest_task_logs_log_level" ON "public"."mcp_ingest_task_logs" USING btree (
    "log_level" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table mcp_ingest_task_logs
-- ----------------------------
ALTER TABLE "public"."mcp_ingest_task_logs" ADD CONSTRAINT "chk_log_level" CHECK (log_level::text = ANY (ARRAY['INFO'::character varying, 'WARN'::character varying, 'ERROR'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table mcp_ingest_task_logs
-- ----------------------------
ALTER TABLE "public"."mcp_ingest_task_logs" ADD CONSTRAINT "mcp_ingest_task_logs_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table mcp_ingest_task_logs
-- ----------------------------
ALTER TABLE "public"."mcp_ingest_task_logs" ADD CONSTRAINT "fk_mcp_ingest_task_logs_task_id" FOREIGN KEY ("task_id") REFERENCES "public"."mcp_ingest_tasks" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
