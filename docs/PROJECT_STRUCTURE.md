# 项目结构

```text
java-agent-platform/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── agent-common/
│   └── src/main/java/com/agentplatform/common/
│       ├── api/ApiResponse.java
│       ├── exception/
│       └── model/
├── agent-core/
│   └── src/main/java/com/agentplatform/core/
│       ├── agent/
│       ├── config/
│       ├── persistence/
│       ├── registry/
│       ├── scheduler/
│       └── task/
├── agent-code-review/
│   └── src/main/java/com/agentplatform/codereview/
│       ├── agent/
│       ├── cache/
│       ├── config/
│       ├── controller/
│       ├── exception/
│       ├── model/
│       ├── persistence/
│       ├── rag/
│       ├── service/
│       ├── store/
│       └── util/
├── agent-server/
│   ├── src/main/java/com/agentplatform/server/
│   │   ├── config/
│   │   ├── controller/
│   │   └── exception/
│   ├── src/main/resources/application.yml
│   └── src/test/resources/application.yml
└── docs/
    ├── ARCHITECTURE.md
    ├── DEPLOY_LOCAL.md
    ├── DEPLOY_DOCKER.md
    ├── PROJECT_STRUCTURE.md
    └── RESUME.md
```
