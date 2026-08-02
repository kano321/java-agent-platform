# 前端联调说明

本项目已经将 `AISuspendedBallChat` 的 UMD 构建产物打入 `agent-server` 的静态资源目录。启动后端后，浏览器直接访问 `http://localhost:8080` 即可使用悬浮球 AI 助手。

主页面同时提供平台控制台，包含以下功能页：

- 总览：健康状态、Agent 数量、任务数量、审查报告数量和 RAG 文档数量
- Agents：查看已注册 Agent 及其状态
- 任务：创建 Demo 任务、查看任务状态、查看和实时订阅任务日志
- 代码审查：填写仓库路径发起 Java 代码审查，查看任务日志和 Markdown 审查报告
- RAG：检索审查文档，索引新的文档
- 对话：直接调用 `/api/v1/chat/stream` 与 AI 对话

悬浮球支持：

- 流式响应
- 图片上传
- 语音输入和语音播报
- 本地历史记录管理
- 历史记录搜索
- 上下文记忆
- 深度思考模式
- 全屏切换

悬浮球使用“项目介绍助手”系统提示词，负责介绍 java-agent-platform 的功能、接口和使用方式。提示词文件位于 `agent-server/src/main/resources/prompts/project-guide.txt`。

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

未配置 `LLM_API_KEY` 时，聊天接口会返回本地联调提示，方便先验证前后端链路。需要真实模型时，在启动前设置：

```powershell
$env:LLM_API_KEY='sk-...'
$env:LLM_BASE_URL='https://api.openai.com/v1'
$env:LLM_MODEL_NAME='gpt-4o-mini'
.\scripts\run.ps1
```
