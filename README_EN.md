# spring-mcps / Dify Ingestion Service

A production-ready Spring Boot microservice designed to bypass Dify's document parsing limitations, providing high-performance document downloading, advanced layout parsing, semantic enrichment (via Visual Language Models), and seamless database ingestion.

> [!NOTE]
> This repository is a part of the `spring-mcps` initiative, aiming to provide enterprise Java developers with robust implementations of Model Context Protocol (MCP) servers and data ingestion pipelines for Large Language Models (LLMs).

---

## Key Highlights

- 🚀 **End-to-End Pipeline**: Handles download, parsing, RAG enhancement, and ingestion in one unified workflow.
- 🤖 **Multi-VLM Integration**: Uses OpenAI GPT-4o, Qwen-VL, or Ollama to run concurrent visual processing for extraction of layout and image semantics.
- 📊 **Task Management**: Supports both synchronous and asynchronous ingestion tasks with detailed status tracking and database logs.
- 🎨 **Vue 3 Dashboard**: Features a modern Element Plus monitoring dashboard showing real-time statistics, execution logs, and Markdown preview.
- ⚡ **Optimized Performance**: Highly concurrent VLM calls (reduced 10-image analysis time from 50s to 5s), connection pooling, and graceful fallback mechanisms.

---

## Core Features

### 1. Document Processing & Parsing
- **HTTP/HTTPS Download**: Auto-manages temporary storage and cleanups.
- **Advanced Layout Engine**: Integrated with MinerU for complex PDF/layout extraction (tables, formulas, layouts to Markdown).
- **MinIO Image Mapping**: Automatically queries PostgreSQL to map, cache, and replace standard image paths with public MinIO URLs.

### 2. Semantic RAG Enhancement
- **Visual Intelligence**: Analyzes embedded charts, diagrams, and figures.
- **Title Context Injection**: Prepends hierarchical headers to child text chunks to preserve search relevancy after split.
- **Markdown Optimization**:
  - Handles parent-child hierarchical segmentation tagging.
  - Implements table boundaries protection to prevent token truncation.

### 3. Progressive Ingestion Modes
- **Synchronous Ingestion**: `POST /ingest/sync` - Blocks execution for small files, immediately returns dataset ID.
- **Asynchronous Tasks**: `POST /ingest/async` - Dispatches background ingestion workers, ideal for massive datasets. Fully monitorable.

---

## Technical Stack

### Backend
- **Spring Boot 3.5.5**
- **Spring Data JDBC**
- **PostgreSQL 12+**
- **OkHttp 4.12.0** / **Lombok** / **Jackson**

### Frontend
- **Vue 3** (Composition API)
- **Vite**
- **Element Plus**
- **Vue Router** & **Axios**
- **Highlight.js** (Markdown Syntax Highlighting)

---

## Repository Structure

```
com.example.ingest
├── DifyIngestApplication.java          # Spring Boot Bootstrap
├── controller/
│   ├── DocumentIngestController.java   # Document ingestion HTTP endpoints
│   └── IngestTaskController.java       # Task management HTTP endpoints
├── service/
│   ├── DocumentIngestService.java      # Core ingestion pipeline orchestrator
│   ├── SemanticTextProcessor.java      # Markdown parser & RAG enrichment engine
│   └── IngestTaskService.java          # Persistent task logger
├── client/
│   ├── MineruClient.java               # Interface to layout parser
│   ├── DifyClient.java                 # Interface to Dify Dataset APIs
│   └── VlmClient.java                  # Visual Model integration (GPT-4o/Qwen)
├── repository/
│   ├── ToolFileRepository.java         # Image entity metadata mapper
│   └── IngestTaskRepository.java       # Task state database accessor
├── config/                             # Async thread pool, Request filtering, and general beans
└── exception/                          # Global HTTP exception handlers
```

---

## Configuration

Set environment variables to customize:

```bash
# Dify Settings
export DIFY_API_KEY=dataset-your-api-key
export DIFY_BASE_URL=http://your-dify-host/v1

# VLM Settings (Multi-Provider Support)
export VLM_PROVIDER=qwen
export VLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export VLM_API_KEY=sk-your-aliyun-key
export VLM_MODEL=qwen-vl-max-latest
```

---

## Quick Start

### 1. Database Setup
Initialize schema using target scripts:
```bash
psql -U postgres -d dify -f sql/001_create_ingest_tasks_table.sql
psql -U postgres -d dify -f sql/002_create_ingest_task_logs_table.sql
```

### 2. Run the Service
```bash
mvn clean package -DskipTests
java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

### 3. Run the Monitoring Dashboard
```bash
cd frontend
npm install
npm run dev
```
Navigate to `http://localhost:5173`.

---

## API Endpoints Overview

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/api/dify/document/ingest/sync` | Sync block upload & processing |
| **POST** | `/api/dify/document/ingest/async` | Async background worker dispatch |
| **GET** | `/api/dify/document/task/{taskId}` | Retrieve task state metadata |
| **GET** | `/api/dify/document/task/{taskId}/logs`| Fetch detailed workflow execution logs |
| **GET** | `/api/dify/tasks/stats` | Database aggregations (throughput, error rates) |
