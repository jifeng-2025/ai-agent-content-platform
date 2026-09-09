# 基线与选型依据

仅在 A0、环境/依赖变更、架构决策时读取。本文件为历史检查快照，不替代当前代码与服务状态。

## 2. 已核对的基线

仓库位于 `D:/2026codex/ai-passage-creator`，WSL 对应 `/mnt/d/2026codex/ai-passage-creator`。本次检查 Git HEAD 为 `9a7dbf1`，写文档前工作树干净。

| 项目 | 实际情况 / 依据 |
|---|---|
| 部署 | Docker 中运行 ai-passage-frontend、ai-passage-backend、ai-passage-mysql、ai-passage-redis；前端宿主端口 80，后端 8123 |
| Java | pom.xml：JDK 21、Spring Boot 3.5.9、Spring AI Alibaba Agent Framework / DashScope 1.1.0.0-RC2 |
| 前端 | frontend/package.json：Vue 3.5、TypeScript、Vite 7、Ant Design Vue、Pinia；有 package-lock.json |
| 数据 | MySQL 8 + MyBatis-Flex；Redis 7 用于现有会话/缓存等；保留现有数据结构与数据卷 |
| 编排 | ArticleAgentOrchestrator 的三个阶段使用 StateGraph；正文 → 配图分析 → 并行配图 → 合成为固定边 |
| 已有能力 | 标题选择、大纲编辑、SSE、图文合成、历史记录、执行日志、图库/生成图/示意图策略 |
| 待核实/补齐 | AgentConfig 定义 MemorySaver 和 maxIterations，但已检查的编排入口直接 graph.compile()；不能据此宣称有持久化恢复或生效的全局循环预算 |
| 测试基线 | 发现 MainApplicationTests；前端脚本未声明 test/e2e，当前测试覆盖及通过情况待 A0 测量 |

已查看任务“查找Agent图文平台并配置”。工具返回了早期 Docker/WSL 配置记录，最近部分轮次正文为空；当前部署判断以本地代码、容器清单和用户使用截图为依据，不假装读取到缺失内容。

截图暴露的产品问题：选题“普通人如何用 AI 提升工作效率”，正文出现大量专业术语、精确百分比和机构归因，可见区域没有对应来源。它提示**受众偏离、论断依据与可读性**需要评测；仅凭截图不能认定整篇文章所有事实错误，也不能把其中技术设想当成项目需求。

## 3. 背景与硬件约束

- 用户有 Python / 深度学习、数字人、3D、RAG 相关学习与项目背景；正在学习 Tool Calling、ReAct、Context Engineering、State、Memory、Evals。具体个人贡献与熟练度后续据实补充，不默认已精通 Java。
- 2026-09-08 本机实测：Windows 11、i7-14700HX、约 16GB 内存、RTX 4060 Laptop 8GB。磁盘当时 C 盘余约 21.5GB、D 盘余约 189.7GB；空间是历史快照。
- 目前已经部署使用，不沿用此前“未安装 Docker”的旧结论。宿主 JDK/Node 是否独立配置仍需开发时核验，容器可运行不代表宿主编译链齐全。
- 文本、视觉评审、图像生成、Embedding 默认使用云端 API；图库检索与 AI 生图分别展示来源。**本地模型不是依赖，GPU 不参与默认运行。**
- 保留 Windows + WSL2 / Docker 工作方式，不要求换 Mac 或扩内存。建议开发与评测串行，初始内容任务并发 1、配图并发上限 2，再按实测调整。
- 保留一个 Java 业务后端；仓库中的 python-backend / go-backend 是其他实现，不同时演进。Python 可用于离线评测，TypeScript 用于开发 Harness。

## 4. 选型记录：已选 B，A 为第一阶段

工作量为单人聚焦开发的粗估，不包含 API 故障、基础补课及求职事务；不是交付保证。

| 方案 | 核心改造 | 适配与取舍 | 粗估 |
|---|---|---|---|
| A：可靠创作增强版 | 原流程 + Writer/Reviewer 有界修订 + 任务恢复 + 基础开发验证 Harness | 复用最多、最快形成演示；研究/RAG 深度有限，适合时间紧 | 6–10 个工作日 |
| **B：可溯源 Research & Content Agent（推荐）** | A + 工具驱动检索、证据引用、按需 RAG、用户偏好、MCP 接入、内容评测 + 可执行 Codex Harness | 兼顾已有 Java 基座和用户 RAG/多模态背景；工程亮点集中，16GB + 云 API 可做 | 15–25 个工作日 |
| C：Python Agent Runtime 路线 | 保留 Vue/Java 业务接口，另建 Python/LangGraph Runtime，增加相同创作与评测能力 | 适合明确主投 Python Agent 岗；贴近 Python 经验，但增加跨服务状态、鉴权、SSE、部署维护 | 20–35 个工作日 |

用户已于 2026-09-09 选择 B，并要求先完成 A。选择 B 的原因：当前已有可用 Java 图文应用，迁移框架不是最急问题；证据驱动创作可以发挥 RAG 背景；图文匹配评审可以发挥多模态经验；恢复、预算和独立验收能够展示实际 Agent 工程能力。先完成 A，B 的扩展不影响最小求职演示。

暂不纳入：通用 Agent OS、十几个互聊 Agent、强化学习训练、自动微调、区块链存证、Kubernetes、多框架并存、自动发布社交平台。技能模板和子 Agent 仅在测得收益后增加。

## 8. 代码入口与变更规则

- 主编排：src/main/java/com/yupi/template/agent/ArticleAgentOrchestrator.java。
- 节点：src/main/java/com/yupi/template/agent/agents/；状态：model/dto/article/ArticleState.java。
- 配置：agent/config/AgentConfig.java；现有服务：service/ArticleAgentService.java；先确认实际路由与开关，避免只改未使用路径。
- 日志：aop/AgentExecutionAspect.java、model/entity/AgentLog.java；配图降级：service/ImageServiceStrategy.java。
- 页面：frontend/src；SQL：sql；部署：根目录 docker-compose.yml。新数据结构使用增量迁移，先备份，不删除卷或重建用户数据。
- 保持 Vue、MySQL、Redis 和已有账号/创作功能。新事件可扩展但兼容旧 SSE 解析；接口变化同步前后端与契约测试。
- 当前 Spring AI Alibaba 是 RC 依赖：单独做版本兼容验证与依赖锁定，再决定升级；不直接按官网 main 分支示例替换全部 API。
- 前端沿用 npm 锁文件。现有 lint 含 --fix，不能当只读验收；pure-build 跳过类型检查，不能等同完整 build。先补充独立只读检查入口。
- 拟用验证命令：后端 mvn test（先核对 JDK/Maven 或容器工具链）；前端 npm run type-check、npm run build；后续新增 E2E。本文不声称这些命令已经通过，也不假定仓库已有 Maven Wrapper。
- 每个任务交付代码、必要测试、运行证据与 status.md 的进度更新；仅文档修改做链接/内容/Git diff 检查即可。
