# Bug Fix Record

## 2024-12-18: VLM 线程池队列溢出

**问题描述**：
- 大文件包含大量图片时，VLM 线程池队列满（200），任务被拒绝
- 错误：`RejectedExecutionException: Task rejected from pool size = 16, queued tasks = 200`

**解决方案**：
- 队列容量从 200 增至 500
- 拒绝策略改为 `CallerRunsPolicy`（队列满时由调用线程执行，提供背压）

## 2024-12-18: 数据库连接重试机制

**问题描述**：
- 应用启动时数据库未就绪导致连接失败，应用直接崩溃

**解决方案**：
- 新增 `DataSourceConfig` 配置类
- 启动时最多重试 5 次，每次间隔 3 秒
- 配置 HikariCP 连接池参数和连接测试

## 2024-12-18: VLM 图片分析并发化重构

**问题描述**：
- `SemanticTextProcessor` 使用串行 `while` 循环调用 VLM 分析图片，效率低下
- `VlmClient.analyzeImageAsync` 使用 `ForkJoinPool.commonPool()`，高并发时阻塞系统线程池
- HTTP 超时设置为 3 分钟，单 GPU 环境下并发请求排队导致超时

**解决方案**：

1. **AsyncConfig.java**：
   - 新增 `vlmExecutor` Bean（核心 8 线程，最大 16 线程）
   - 专用于 VLM API 调用，避免污染公共线程池

2. **VlmClient.java**：
   - 注入 `vlmExecutor` 自定义线程池
   - `analyzeImageAsync` 使用 `CompletableFuture.supplyAsync(..., vlmExecutor)`
   - HTTP `readTimeout` 从 3 分钟延长至 10 分钟，适配 GPU 排队场景

3. **SemanticTextProcessor.java**：
   - 重构 `enrichImageDescriptionsWithVlm` 为并发模式：
     - 先收集所有图片任务
     - 使用 `Stream` 并发提交所有 `CompletableFuture`
     - `CompletableFuture.allOf(...).join()` 等待所有任务完成
     - 批量替换 Markdown 中的图片标签
   - 新增 `ImageTask` 和 `ImageTaskResult` 内部类

**性能提升**：
- 多图片文档处理时间从 O(n) 降至 O(1)（受 GPU 吞吐量限制）
- CPU/IO 利用率显著提升
- 避免 ForkJoinPool 阻塞风险
