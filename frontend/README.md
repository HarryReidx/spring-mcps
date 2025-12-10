# TyAiFlow 文档入库监控前端

基于 Vue 3 + Vite + Element Plus 的任务监控大屏。

## 功能特性

- 📊 **统计概览**：总任务数、成功率、实时状态统计
- 📋 **任务列表**：分页查询、状态筛选、文件大小显示
- 🔍 **任务详情**：查看完整的任务信息、Markdown 预览、VLM 耗时日志
- 🖼️ **图片处理**：MinIO 自动上传、VLM 图片分析（带上下文）
- ⚡ **性能优化**：大文件超时调整（30分钟）、VLM 超时控制（30秒）

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

访问：http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

## 技术栈

- Vue 3 (Composition API)
- Vue Router 4
- Element Plus
- Axios
- Highlight.js (Markdown 语法高亮)
- Vite

## 目录结构

```
frontend/
├── src/
│   ├── api/           # API 接口
│   ├── views/         # 页面组件
│   ├── App.vue        # 根组件
│   └── main.js        # 入口文件
├── index.html
├── vite.config.js
└── package.json
```

## API 接口

后端接口地址：`http://localhost:8080/api/dify`

### 任务管理
- `GET /document/task/stats` - 获取统计信息
- `GET /document/tasks` - 获取任务列表（分页）
- `GET /document/task/{id}` - 获取任务详情
- `GET /document/task/{id}/logs` - 获取任务日志
- `DELETE /document/tasks` - 批量删除任务

### 文档入库
- `POST /document/ingest/sync` - 同步入库
- `POST /document/ingest/async` - 异步入库
- `POST /document/ingest` - 默认异步入库

## 最新更新

### 2025-12-09
- ✅ 新增文件大小记录和显示
- ✅ VLM 分析超时优化（30秒）
- ✅ MinIO 图片自动上传
- ✅ VLM 图片分析增加上下文（前后各20字符）
- ✅ 必填参数校验优化
