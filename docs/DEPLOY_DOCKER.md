# Docker 部署

## 1. 构建并启动

```powershell
$env:LLM_API_KEY='sk-xxx'   # 可选，不配置则使用规则评审
docker compose up -d --build
```

服务：

- 应用：`http://localhost:8080`
- MySQL：`localhost:3306`
- Redis：`localhost:6379`

## 2. 验证

```powershell
curl.exe http://localhost:8080/api/v1/health
curl.exe http://localhost:8080/api/v1/agents
```

## 3. 查看日志

```powershell
docker compose logs -f app
```

## 4. 停止

```powershell
docker compose down
```

保留数据：

```powershell
docker compose down -v
```

## 5. 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `LLM_API_KEY` | 空 | 启用 LLM 增强评审 |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | 兼容 OpenAI API |
| `LLM_MODEL_NAME` | `gpt-4o-mini` | 模型名 |
| `DB_URL` | Compose 内置 MySQL | 数据库连接 |
| `REDIS_HOST` | `redis` | Redis 地址 |
| `REDIS_CACHE_ENABLED` | `true` | Redis 缓存开关 |
| `MILVUS_ENABLED` | `false` | 启用 Milvus 向量库 |
| `MILVUS_HOST` | `host.docker.internal` | Milvus 地址 |
| `MILVUS_PORT` | `19530` | Milvus gRPC 端口 |
| `MILVUS_COLLECTION_NAME` | `agent_platform_embeddings` | Milvus Collection |
| `MILVUS_USERNAME` / `MILVUS_PASSWORD` | 空 | Milvus 认证（可选） |

如果你的 Milvus 运行在宿主机上，容器内使用 `host.docker.internal` 访问。
