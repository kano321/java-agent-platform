# 简历级项目描述

## 项目名称

基于 LangChain4j 的企业级 Java 代码评审 Agent 平台

## 一句话描述

使用 Spring Boot 3 + LangChain4j 构建的多智能体代码评审平台，支持 Java 仓库扫描、静态分析、LLM 增强评审、RAG 历史知识检索、异步任务与 SSE 实时日志。

## 技术栈

Spring Boot 3.5、LangChain4j 1.18、JavaParser、JGit、Spring Data JPA、MySQL、Redis、Docker Compose、JUnit 5、Maven 多模块

## 项目亮点

- 自研 Agent 注册中心，支持注册、注销、心跳与过期回收
- 异步任务状态机与线程池隔离，任务状态、日志全流程可观测
- SSE 实时推送任务日志与状态，支持断线重连后的日志回放
- 基于 JavaParser 的规则静态分析 + LangChain4j LLM 增强双通道评审
- 评审报告自动生成 Markdown 并持久化到 MySQL 与本地文件
- 基于向量检索的 RAG 模块，支持 Milvus 与内存向量库，历史评审报告可被后续评审引用
- Redis 缓存与本地降级策略，保证无外部中间件时仍可运行
- Docker Compose 一键部署 MySQL、Redis 与应用

## 技术难点

- LangChain4j 1.18 API 变更迁移，`ChatLanguageModel` 到 `ChatModel`
- JavaParser AST 遍历与规则检测，避免误报与重复问题
- JGit diff 与工作区扫描结合，支持全量评审与增量评审
- 多 Spring Data 模块共存时的 Repository 扫描与严格模式配置
- SSE 连接生命周期管理，任务结束后主动关闭 Emitter 防止线程泄漏
- 无 LLM Key 时自动降级到规则评审，保证项目可离线演示

## 简历描述模板

负责设计并实现企业级 Java 代码评审 Agent 平台，采用 Maven 多模块架构与 Spring Boot 3、LangChain4j、JavaParser、JGit、MySQL、Redis 等核心技术。实现 Agent 注册中心、异步任务引擎、SSE 流式日志、代码静态分析、LLM 增强评审、Markdown 报告生成、RAG 历史知识检索、Redis 缓存和 Docker Compose 部署。通过单元测试与接口集成测试覆盖核心链路，保证系统在无外部 LLM 配置时仍可完整运行。
