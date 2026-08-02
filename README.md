# java-agent-platform

企业级 Java Agent 平台，基于 Spring Boot 3、LangChain4j、JavaParser、JGit、MySQL、Redis 构建，核心业务是 Java 代码评审。

## 功能

- Maven 多模块：`agent-common` / `agent-core` / `agent-code-review` / `agent-server`
- Agent 注册中心：注册、注销、心跳、过期清理
- 异步任务引擎：PENDING / RUNNING / SUCCEEDED / FAILED / CANCELED 状态机
- SSE 流式日志：任务日志与状态实时推送
- Java 代码评审：JavaParser 静态分析、JGit 仓库扫描、Markdown 报告
- RAG 检索：评审报告向量化索引与相似度查询，支持 Milvus / 内存向量库
- MySQL 持久化：任务、评审报告、评审问题、RAG 文档
- Redis 缓存：评审 Markdown 缓存，本地可降级
- Docker Compose：MySQL + Redis + 应用一键部署

## 模块

| 模块 | 说明 |
| --- | --- |
| `agent-common` | 公共模型、枚举、DTO、异常 |
| `agent-core` | Agent 注册中心、任务引擎、异步执行、SSE 总线 |
| `agent-code-review` | JavaParser/JGit 分析、评审 Agent、Markdown 报告、RAG |
| `agent-server` | Spring Boot 启动模块、REST/SSE 接口、LangChain4j 配置 |

## 快速开始

```powershell
.\mvnw.cmd clean test
.\scripts\run.ps1
```

默认端口：`8080`

## 大模型配置

编辑项目根目录的 `.env` 文件，只需配置一次，之后直接启动即可：

```dotenv
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL_NAME=gpt-4o-mini
```

本地启动脚本和 Docker Compose 都会读取该文件。`.env` 已在 `.gitignore` 中，密钥不会提交。未填写 `LLM_API_KEY` 时，代码评审使用纯规则分析，对话接口返回本地联调提示。

## 主要 API

- `GET  /api/v1/health`
- `GET  /api/v1/agents`
- `POST /api/v1/agents/register`
- `POST /api/v1/tasks`
- `GET  /api/v1/tasks/{taskId}/events` SSE
- `POST /api/v1/reviews`
- `GET  /api/v1/reviews/{reportId}/markdown`
- `POST /api/v1/rag/documents`
- `GET  /api/v1/rag/search`
- `POST /api/v1/chat`
- `POST /api/v1/chat/stream` SSE

前端已内置平台控制台，启动后访问 `http://localhost:8080` 即可查看 Agent、任务、代码审查、RAG 和 AI 对话。详细说明见 [前端联调说明](docs/FRONTEND_INTEGRATION.md)。

## 文档

- [架构图](docs/ARCHITECTURE.md)
- [本地部署](docs/DEPLOY_LOCAL.md)
- [Docker 部署](docs/DEPLOY_DOCKER.md)
- [项目结构](docs/PROJECT_STRUCTURE.md)

## Milvus 配置

```powershell
$env:MILVUS_ENABLED='true'
$env:MILVUS_HOST='localhost'
$env:MILVUS_PORT='19530'
$env:MILVUS_COLLECTION_NAME='agent_platform_embeddings'
```

不配置 `MILVUS_ENABLED` 时，系统自动使用本地内存向量库，方便开发和测试。

## 版本锁定

- Spring Boot 3.5.16
- LangChain4j 1.18.1
- JavaParser 3.28.2
- JGit 7.7.1
- Java 17+
