# 官方参考与版本约定

仅在更换技术、升级依赖或核对工具能力时读取。当前不是要求升级所有依赖。

## 11. 设计依据（2026-09-09 查阅）

- [Spring AI Alibaba 官方仓库](https://github.com/alibaba/spring-ai-alibaba)：Graph、LoopAgent、上下文管理、人工介入和 MCP 等已有框架能力；具体可用 API 以锁定依赖版本验证。
- [LangGraph 官方概览](https://docs.langchain.com/oss/python/langgraph/overview)：持久执行、流式与人机协作，以及确定性步骤和 Agent 步骤混合；作为方案 C 的依据。
- [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)：工作流/Agent 区别、简单可组合设计、评价修订模式；该文是历史设计依据，不称为最新发布。
- [Codex SDK](https://learn.chatgpt.com/docs/codex-sdk)：程序化启动、继续、恢复编码任务；开发 Harness 的可选执行接口。
- [Codex 非交互模式](https://learn.chatgpt.com/docs/non-interactive-mode)：脚本集成、结构化事件和执行控制。
- [AGENTS.md 官方约定](https://learn.chatgpt.com/docs/agent-configuration/agents-md)：自动发现的入口是 AGENTS.md；本项目用该入口引用用户指定的 agent.md，避免两份路线漂移。

- [Docker Compose 单机生产部署](https://docs.docker.com/compose/how-tos/production/)
- [GitHub Actions 发布容器镜像](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)

2026-09-09 再次核对：保留现有 Compose 为本地部署入口；GitHub Actions + GHCR + 单机 Compose 为拟采用的发布路线。CI/镜像工作流尚未实现。
