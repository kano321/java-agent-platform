# 本地部署

## 1. 环境要求

- JDK 17+
- Maven 无需安装，项目内置 Maven Wrapper
- 推荐 Docker（用于本地启动 MySQL 和 Redis）

## 大模型配置

编辑项目根目录的 `.env` 文件，填写 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL_NAME`。本地启动时系统会自动读取该文件，不需要每次在终端设置环境变量。

## 2. 单元测试（H2，不依赖外部服务）

```powershell
$env:JAVA_HOME='F:\jdk21'
.\mvnw.cmd clean test
```

## 3. 本地快速启动（H2 + 内存向量库，无需 MySQL/Redis/Milvus）

```powershell
.\scripts\run.ps1
```

等价于：

```powershell
.\mvnw.cmd -pl agent-server -am spring-boot:run "-Dspring-boot.run.profiles=local"
```

## 4. 本地启动 Milvus（可选）

如果你已经用 Docker 部署了 Milvus，只需启用配置：

```powershell
$env:MILVUS_ENABLED='true'
$env:MILVUS_HOST='localhost'
$env:MILVUS_PORT='19530'
$env:MILVUS_COLLECTION_NAME='agent_platform_embeddings'
.\scripts\run.ps1
```

未安装 Milvus 时保持 `MILVUS_ENABLED=false`，系统使用内存向量库。

## 5. 启动 MySQL 和 Redis（完整本地模式）

```powershell
docker compose up -d mysql redis
```

## 6. 启动应用（完整本地模式）

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/agent_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='agent'
$env:DB_PASSWORD='agent123456'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_CACHE_ENABLED='true'
.\mvnw.cmd -pl agent-server -am spring-boot:run
```

## 7. 验证

```powershell
curl.exe http://localhost:8080/api/v1/health
curl.exe http://localhost:8080/api/v1/agents
```

## 8. 触发代码评审

```powershell
curl.exe -X POST http://localhost:8080/api/v1/reviews -H "Content-Type: application/json" -d "{\"repoPath\":\"E:/study/langchain/helloagents/hello-agents/Co-creation-projects/java-agent-platform\"}"
```

等待任务 `SUCCEEDED` 后：

```powershell
curl.exe "http://localhost:8080/api/v1/reviews?taskId={taskId}"
curl.exe "http://localhost:8080/api/v1/reviews/{reportId}/markdown"
curl.exe "http://localhost:8080/api/v1/rag/search?query=OrderService&limit=5"
```
