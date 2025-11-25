# Dify 文档入库服务 - 部署指南

本文档详细说明如何部署后端服务和前端监控系统。

## 环境要求

### 后端
- JDK 17 或更高版本
- Maven 3.6+（仅编译时需要）
- PostgreSQL 12+（必需，用于图片路径替换和任务管理）
- 网络访问：MinerU、Dify API、MinIO

### 前端
- Node.js 16 或更高版本
- npm 或 yarn 包管理器

## 当前环境配置

### 数据库
- **Host**: 172.24.0.5:5432
- **Database**: dify
- **Username**: postgres
- **Password**: difyai123456

### Dify API
- **Base URL**: http://172.24.0.5/v1
- **API Key**: dataset-CxGlfh0xHkUoCts6dj17XUhw

### MinerU 服务
- **Base URL**: http://172.24.0.5:8000
- **Server Type**: local

### MinIO 存储
- **Image Path Prefix**: http://172.24.0.5:9000/ty-ai-flow

## 部署步骤

### 0. 数据库初始化

执行 SQL 脚本创建任务表：

```bash
# Windows
psql -U postgres -d dify -f sql\001_create_ingest_tasks_table.sql
psql -U postgres -d dify -f sql\002_add_execution_mode.sql
psql -U postgres -d dify -f sql\004_change_jsonb_to_text.sql

# Linux/Mac
psql -U postgres -d dify -f sql/001_create_ingest_tasks_table.sql
psql -U postgres -d dify -f sql/002_add_execution_mode.sql
psql -U postgres -d dify -f sql/004_change_jsonb_to_text.sql
```

或手动执行 SQL：

```sql
-- 连接数据库
psql -U postgres -d dify

-- 创建任务表
CREATE TABLE ingest_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_url TEXT,
    file_type VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    execution_mode VARCHAR(20),
    enable_vlm BOOLEAN DEFAULT FALSE,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_msg TEXT,
    result_summary TEXT,
    parsed_markdown TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_ingest_tasks_status ON ingest_tasks(status);
CREATE INDEX idx_ingest_tasks_dataset_id ON ingest_tasks(dataset_id);
CREATE INDEX idx_ingest_tasks_created_at ON ingest_tasks(created_at DESC);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_ingest_tasks_updated_at BEFORE UPDATE
    ON ingest_tasks FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

验证：
```sql
\dt ingest_tasks
SELECT * FROM ingest_tasks LIMIT 1;
```

### 1. 编译打包后端

```bash
cd /path/to/spring-mcps
mvn clean package -DskipTests
```

生成的 JAR 包位于：`target/dify-ingest-0.0.1-SNAPSHOT.jar`

### 2. 配置环境变量（可选）

如需覆盖默认配置，可设置环境变量：

```bash
export DIFY_API_KEY=your-api-key
export DIFY_BASE_URL=http://your-dify-host/v1
export MINERU_BASE_URL=http://your-mineru-host:8000
export MINIO_IMG_PREFIX=http://your-minio-host:9000/bucket
export DB_URL=jdbc:postgresql://your-host:5432/dify
export DB_USERNAME=postgres
export DB_PASSWORD=your-password
```

### 3. 启动服务

#### 方式 1: 直接运行
```bash
java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

#### 方式 2: 后台运行
```bash
nohup java -jar target/dify-ingest-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

#### 方式 3: 使用 systemd（推荐）

创建服务文件 `/etc/systemd/system/dify-ingest.service`：

```ini
[Unit]
Description=Dify Document Ingest Service
After=network.target

[Service]
Type=simple
User=your-user
WorkingDirectory=/path/to/spring-mcps
ExecStart=/usr/bin/java -jar /path/to/spring-mcps/target/dify-ingest-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl start dify-ingest
sudo systemctl enable dify-ingest
sudo systemctl status dify-ingest
```

查看日志：
```bash
sudo journalctl -u dify-ingest -f
```

### 4. 部署前端（可选）

#### 开发模式

```bash
cd frontend
npm install
npm run dev
```

访问：http://localhost:5173

#### 生产部署

```bash
cd frontend
npm install
npm run build
```

构建产物在 `dist/` 目录，可以部署到 Nginx：

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    root /path/to/frontend/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 5. 验证部署

```bash
# 后端健康检查
curl http://localhost:8080/api/dify/document/health
# 预期响应: OK

# 任务统计
curl http://localhost:8080/api/dify/tasks/stats
# 预期响应: JSON 统计信息

# 前端访问（如已部署）
# 浏览器访问: http://localhost:5173
```

## 配置说明

### 必需配置

以下配置必须正确设置：

- `app.dify.api-key`: Dify API 密钥
- `app.dify.base-url`: Dify API 地址
- `app.mineru.base-url`: MinerU 服务地址

### 可选配置

以下配置可选，用于启用图片路径替换功能：

- `spring.datasource.url`: PostgreSQL 连接地址
- `spring.datasource.username`: 数据库用户名
- `spring.datasource.password`: 数据库密码
- `app.minio.img-path-prefix`: MinIO 图片路径前缀

**注意**：如果不配置数据库，服务仍可正常运行，但图片路径不会被替换。

### 分段配置

可调整文档分段参数：

```yaml
app:
  chunking:
    separator: "\n"        # 分段分隔符
    max-tokens: 1000       # 最大 token 数
    chunk-overlap: 50      # 分段重叠
```

## 性能优化

### JVM 参数

```bash
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/dify-ingest-0.0.1-SNAPSHOT.jar
```

### 数据库连接池

修改 `application.yml`：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### HTTP 超时

如需处理大文件，可调整超时时间（修改源码）：

```java
// MineruClient.java 和 DifyClient.java
private OkHttpClient getHttpClient() {
    return new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)  // 增加读取超时
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
}
```

## 监控

### 健康检查

```bash
curl http://localhost:8080/actuator/health
```

### 查看指标

```bash
curl http://localhost:8080/actuator/metrics
```

### 日志级别

开发环境：
```yaml
logging:
  level:
    com.example.ingest: DEBUG
```

生产环境：
```yaml
logging:
  level:
    com.example.ingest: INFO
    org.springframework: WARN
```

## 故障排查

### 服务无法启动

1. 检查端口是否被占用：
   ```bash
   netstat -an | grep 8080
   ```

2. 查看日志：
   ```bash
   tail -f logs/spring.log
   # 或
   sudo journalctl -u dify-ingest -n 100
   ```

### 数据库连接失败

1. 检查 PostgreSQL 服务：
   ```bash
   pg_isready -h 172.24.0.5 -p 5432
   ```

2. 测试连接：
   ```bash
   psql -h 172.24.0.5 -p 5432 -U postgres -d dify
   ```

3. 如果数据库不可用，服务会自动降级运行（跳过图片路径替换）

### MinerU 调用失败

1. 检查 MinerU 服务：
   ```bash
   curl http://172.24.0.5:8000/docs
   ```

2. 检查网络连通性：
   ```bash
   ping 172.24.0.5
   ```

### Dify API 调用失败

1. 验证 API Key：
   ```bash
   curl -H "Authorization: Bearer YOUR_API_KEY" \
        http://172.24.0.5/v1/datasets
   ```

2. 检查 Dataset ID 是否存在

## Docker 部署（可选）

### 创建 Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/dify-ingest-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 构建镜像

```bash
docker build -t dify-ingest:latest .
```

### 运行容器

```bash
docker run -d \
  --name dify-ingest \
  -p 8080:8080 \
  -e DIFY_API_KEY=your-key \
  -e MINERU_BASE_URL=http://mineru:8000 \
  dify-ingest:latest
```

### 使用 Docker Compose

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  dify-ingest:
    image: dify-ingest:latest
    ports:
      - "8080:8080"
    environment:
      - DIFY_API_KEY=your-key
      - DIFY_BASE_URL=http://dify:80/v1
      - MINERU_BASE_URL=http://mineru:8000
      - DB_URL=jdbc:postgresql://postgres:5432/dify
      - DB_USERNAME=postgres
      - DB_PASSWORD=your-password
      - MINIO_IMG_PREFIX=http://minio:9000/bucket
    restart: unless-stopped
```

启动：
```bash
docker-compose up -d
```

## 安全建议

1. **不要在代码中硬编码敏感信息**，使用环境变量
2. **限制网络访问**，只允许必要的 IP 访问服务
3. **定期更新依赖**，修复安全漏洞
4. **启用 HTTPS**，使用反向代理（如 Nginx）
5. **监控日志**，及时发现异常行为

## 备份与恢复

### 备份

只需备份配置文件：
```bash
cp src/main/resources/application.yml application.yml.backup
```

### 恢复

1. 恢复配置文件
2. 重新编译打包
3. 重启服务

## 部署检查清单

### 部署前检查

- [ ] JDK 17+ 已安装
- [ ] Maven 已安装（或使用 mvnw）
- [ ] PostgreSQL 12+ 已安装并运行
- [ ] Node.js 16+ 已安装（如需前端）
- [ ] 数据库 `dify` 已创建
- [ ] 数据库连接配置正确
- [ ] MinerU 服务可访问
- [ ] Dify API 可访问

### 部署后验证

```bash
# 1. 后端健康检查
curl http://localhost:8080/api/dify/document/health
# 预期: OK

# 2. 任务统计
curl http://localhost:8080/api/dify/tasks/stats
# 预期: JSON 统计信息

# 3. 创建测试任务
curl -X POST http://localhost:8080/api/dify/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "datasetId": "test",
    "fileUrl": "http://example.com/test.pdf",
    "fileName": "test.pdf",
    "fileType": "pdf"
  }'
# 预期: 返回任务 ID

# 4. 前端访问（如已部署）
# 浏览器访问: http://localhost:5173
```

### 数据库验证

```sql
-- 查看表结构
\d ingest_tasks

-- 查看任务列表
SELECT id, file_name, status, execution_mode, created_at 
FROM ingest_tasks 
ORDER BY created_at DESC 
LIMIT 10;

-- 统计各状态任务数
SELECT status, COUNT(*) 
FROM ingest_tasks 
GROUP BY status;
```

## 升级

### 升级步骤

1. 停止服务：
   ```bash
   sudo systemctl stop dify-ingest
   ```

2. 备份当前版本：
   ```bash
   cp target/dify-ingest-0.0.1-SNAPSHOT.jar dify-ingest-backup.jar
   ```

3. 更新代码并重新编译：
   ```bash
   git pull
   mvn clean package -DskipTests
   ```

4. 执行数据库升级脚本（如有）：
   ```bash
   psql -U postgres -d dify -f sql/002_add_execution_mode.sql
   ```

5. 启动服务：
   ```bash
   sudo systemctl start dify-ingest
   ```

6. 验证：
   ```bash
   curl http://localhost:8080/api/dify/document/health
   curl http://localhost:8080/api/dify/tasks/stats
   ```

### 回滚

如果升级失败，可以快速回滚：

```bash
# 1. 停止服务
sudo systemctl stop dify-ingest

# 2. 恢复备份
cp dify-ingest-backup.jar target/dify-ingest-0.0.1-SNAPSHOT.jar

# 3. 启动服务
sudo systemctl start dify-ingest
```
