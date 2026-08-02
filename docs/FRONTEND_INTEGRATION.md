# 前端联调说明

启动后端后，浏览器直接访问 `http://localhost:8080` 即可使用平台控制台，包含以下功能页：

- 总览：健康状态、Agent 数量、任务数量、审查报告数量和 RAG 文档数量
- Agents：查看已注册 Agent 及其状态
- 任务：创建 Demo 任务、查看任务状态、查看和实时订阅任务日志
- 代码审查：填写仓库路径发起 Java 代码审查，查看任务日志和 Markdown 审查报告
- RAG：检索审查文档，索引新的文档
- 对话：直接调用 `/api/v1/chat/stream` 与 AI 对话

对话页使用平台 AI Agent 系统提示词，负责帮助用户使用代码审查、任务、RAG 和对话等平台能力。提示词文件位于 `agent-server/src/main/resources/prompts/project-guide.txt`。

## 本地启动

```powershell
.\scripts\run.ps1
```

启动后：

- 前端页面：`http://localhost:8080`
- 健康检查：`http://localhost:8080/api/v1/health`
- 普通对话：`POST http://localhost:8080/api/v1/chat`
- 流式对话：`POST http://localhost:8080/api/v1/chat/stream`

## 前端请求协议

前端组件会发送如下格式的 JSON：

```json
{
  "query": "你好",
  "appName": "java-agent-platform",
  "userId": "local-user",
  "isStream": true,
  "isThinkMode": false,
  "history": [
    { "role": "user", "content": "上一轮问题" },
    { "role": "assistant", "content": "上一轮回答" }
  ]
}
```

普通接口返回：

```json
{
  "code": 0,
  "result": { "answer": "AI 回答内容" }
}
```

流式接口返回 `text/event-stream`，每个数据块是前端可识别的 JSON：

```json
{"code":0,"result":"回答分片","is_end":false}
{"code":0,"result":"","is_end":true}
```

## 大模型配置

未配置 `LLM_API_KEY` 时，聊天接口会返回本地联调提示，方便先验证前后端链路。需要真实模型时，编辑项目根目录的 `.env` 文件：

```dotenv
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL_NAME=gpt-4o-mini
```

然后直接运行 `.\scripts\run.ps1`，无需在终端重复设置环境变量。
