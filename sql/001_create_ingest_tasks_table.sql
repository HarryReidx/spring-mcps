/*
 Navicat Premium Dump SQL

 Source Server         : 172.24.0.5-AiFlow
 Source Server Type    : PostgreSQL
 Source Server Version : 150014 (150014)
 Source Host           : 172.24.0.5:5432
 Source Catalog        : dify
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 150014 (150014)
 File Encoding         : 65001

 Date: 25/11/2025 17:41:12
*/


-- ----------------------------
-- Table structure for ingest_tasks
-- ----------------------------
DROP TABLE IF EXISTS "public"."ingest_tasks";
CREATE TABLE "public"."ingest_tasks" (
                                         "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                         "dataset_id" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
                                         "file_name" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
                                         "file_url" text COLLATE "pg_catalog"."default",
                                         "file_type" varchar(50) COLLATE "pg_catalog"."default",
                                         "status" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'PENDING'::character varying,
                                         "enable_vlm" bool DEFAULT false,
                                         "start_time" timestamp(6),
                                         "end_time" timestamp(6),
                                         "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
                                         "updated_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
                                         "error_msg" text COLLATE "pg_catalog"."default",
                                         "result_summary" text COLLATE "pg_catalog"."default",
                                         "original_doc_url" text COLLATE "pg_catalog"."default",
                                         "parsed_markdown" text COLLATE "pg_catalog"."default",
                                         "execution_mode" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'ASYNC'::character varying,
                                         "vlm_cost_time" bigint DEFAULT 0,
                                         "total_cost_time" bigint DEFAULT 0
)
;
COMMENT ON COLUMN "public"."ingest_tasks"."id" IS '任务唯一标识';
COMMENT ON COLUMN "public"."ingest_tasks"."dataset_id" IS 'Dify 知识库 ID';
COMMENT ON COLUMN "public"."ingest_tasks"."status" IS '任务状态：PENDING-待处理, PROCESSING-处理中, COMPLETED-已完成, FAILED-失败';
COMMENT ON COLUMN "public"."ingest_tasks"."result_summary" IS '任务结果摘要（JSON 格式的文本）';
COMMENT ON COLUMN "public"."ingest_tasks"."parsed_markdown" IS '解析后的 Markdown 内容（用于前端预览）';
COMMENT ON COLUMN "public"."ingest_tasks"."execution_mode" IS '执行模式：SYNC-同步执行, ASYNC-异步执行';
COMMENT ON COLUMN "public"."ingest_tasks"."vlm_cost_time" IS 'VLM 处理耗时（毫秒）';
COMMENT ON COLUMN "public"."ingest_tasks"."total_cost_time" IS '总耗时（毫秒）';
COMMENT ON TABLE "public"."ingest_tasks" IS '文档入库任务表';

-- ----------------------------
-- Indexes structure for table ingest_tasks
-- ----------------------------
CREATE INDEX "idx_ingest_tasks_created_at" ON "public"."ingest_tasks" USING btree (
    "created_at" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_ingest_tasks_dataset_id" ON "public"."ingest_tasks" USING btree (
    "dataset_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_ingest_tasks_execution_mode" ON "public"."ingest_tasks" USING btree (
    "execution_mode" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_ingest_tasks_file_name" ON "public"."ingest_tasks" USING btree (
    "file_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_ingest_tasks_status" ON "public"."ingest_tasks" USING btree (
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Triggers structure for table ingest_tasks
-- ----------------------------
CREATE TRIGGER "update_ingest_tasks_updated_at" BEFORE UPDATE ON "public"."ingest_tasks"
    FOR EACH ROW
    EXECUTE PROCEDURE "public"."update_updated_at_column"();

-- ----------------------------
-- Checks structure for table ingest_tasks
-- ----------------------------
ALTER TABLE "public"."ingest_tasks" ADD CONSTRAINT "chk_status" CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying]::text[]));
ALTER TABLE "public"."ingest_tasks" ADD CONSTRAINT "chk_execution_mode" CHECK (execution_mode::text = ANY (ARRAY['SYNC'::character varying, 'ASYNC'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table ingest_tasks
-- ----------------------------
ALTER TABLE "public"."ingest_tasks" ADD CONSTRAINT "ingest_tasks_pkey" PRIMARY KEY ("id");
