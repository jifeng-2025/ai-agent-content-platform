# A0 基线报告（2026-09-09）

范围：方案 B 已选，先 A；本次仅 A0/H0 基线，不实施 A1。基线 HEAD `9a7dbf1ff58ec68115f2c0c7310b680b2febe370`，分支 main。进场 Git 状态为 `?? AGENTS.md`、`?? agent.md`、`?? docs/`，它们是已有用户改动，未覆盖或加入提交。未提交、推送、修改远端或部署。

## 实际调用路径

- 前端 `frontend/src/pages/article/ArticleCreatePage.vue` → `frontend/src/api/articleController.ts`。
- `/api/article/create` → ArticleController.createArticle → ArticleService.createArticleTaskWithQuotaCheck（配额与任务持久化）→ ArticleAsyncService.executePhase1。
- `/api/article/confirm-title` → ArticleService.confirmTitle → ArticleAsyncService.executePhase2。
- `/api/article/confirm-outline` → ArticleService.confirmOutline → ArticleAsyncService.executePhase3。
- 三阶段都调用 `AgentConfig.isOrchestratorEnabled()`（ArticleAsyncService 第59/118/186行）。true → ArticleAgentOrchestrator；false → ArticleAgentService。AgentConfig `@Value` 默认 true，application.yml 也为 true，服务注释“默认旧模式”不准确。
- 编排：标题节点；大纲节点；正文 → 配图分析 → ParallelImageGenerator → ContentMergerAgent。三个入口在第94/146/206行直接 `graph.compile()`；MemorySaver Bean 与 maxIterations 不能作为已生效的恢复/预算证据。
- `/api/article/ai-modify-outline` 经 ArticleServiceImpl.aiModifyOutline 第324行仍直接走旧 ArticleAgentService.aiModifyOutline，不经过上述开关。
- 实际容器环境 `SPRING_PROFILES_ACTIVE=prod`，未设置 ARTICLE_AGENT_ORCHESTRATOR_ENABLED；源码 prod 配置未覆盖开关。可据此确认源码默认路径，但未对运行 JAR 做源码一致性证明。

## 环境与数据保护

宿主 PATH 有 Codex Node、Docker、WSL，无 java/mvn/npm；WSL 为 Java 11，没有 Maven/Node/npm，不满足 Java 21 构建要求。使用独立、固定 digest 的 JDK21/Maven 与 Node22 工具镜像，版本输出见运行记录。

原平台四个容器保持运行：ai-passage-frontend、ai-passage-backend、ai-passage-mysql、ai-passage-redis。数据卷 `ai-passage-mysql-data`、`ai-passage-redis-data` 未挂入验证环境。未读取用户记录、登录账号、执行数据库迁移、备份导出、清理卷或生产写入。因本次不改数据，未制造含用户数据的备份副本。

源码副本按白名单复制，保留依赖版本/锁文件；忽略本地密钥配置并生成仅副本使用的前端相对 API 地址。现有开发默认端口为8567，prod 为8123；Vite代理指向8567，示例 env.ts 使用8123，配置时需明确运行目标，不把两者默认为同一入口。

## 固定案例及证据边界

| ID | 输入 | 注入/预期 |
|---|---|---|
| C01 | 普通人如何用 AI 提升工作效率 / educational | 固定模型与图片响应；三阶段完成、合成图片、SSE完成 |
| C02 | 周末散步中的小发现 / emotional | 同上，覆盖情感风格输入 |
| C03 | 给新手解释数据备份 / tech | 同上，覆盖技术风格输入 |
| C04 | 整理书桌的三个步骤 / humorous | 非法标题 JSON；FAILED、ERROR、关闭SSE、不保存结果 |
| C05 | 第一次制作旅行清单 / educational | 模型抛异常；FAILED、ERROR、关闭SSE、不保存结果 |

唯一案例清单：`src/test/resources/a0/cases.json`。执行：`A0BaselineTest`。使用真实 Controller方法、异步服务实现、StateGraph 和文本节点，替换模型、配图I/O、持久化、用户与SSE发送边界；不加载 Spring 上下文。此为 Mock 编排回归，不是 HTTP/异步线程/事务/SSE网络或真实生成验收。audience/reviewFocus仅为 A1 评测种子，没有自动质量评分。

## 验证结果

| 检查 | 当前实测 | 证据 |
|---|---|---|
| 后端 package（编译业务和测试，跳过测试执行） | PASS，567843ms | 最终运行 backend-package.txt |
| 5个固定 Mock 首次运行 | 5/5，0 failure/error/skipped | 最终运行 test-dependencies.txt |
| 5个固定 Mock 断网复跑 | 5/5，0 failure/error/skipped | [JUnit摘要](mock-result.txt)；最终运行 mock.txt |
| 前端独立 type-check | PASS，首次112944ms；最终111482ms | 首次运行 type-check.txt |
| 前端完整 build | PASS，首次273275ms；最终249945ms；保留500kB包体积警告 | 首次运行 build.txt |
| 只读 lint:check | FAIL：16 errors，1 warning；现有源码未改 | [完整明细](existing-lint-failures.txt) |
| 现有前后端健康 GET | 均 HTTP200；后端 code=0,data=ok | 独立 health 运行 summary.json |
| 真实供应商文本冒烟 | PASS：HTTP200；一次qwen-plus；49+49=98token；生图0次；金额未知 | [独立记录](real-api-result.json) |
| 原有 contextLoads | FAIL：1 test / 1 error；隔离配置缺少DashScope API key | [失败明细](existing-context-failure.txt) |
| 隔离Vite启动及清理 | PASS；HTTP就绪41749ms；90秒预算；临时容器已清理 | [独立启动记录](dev-startup-result.json) |

首次运行：`runs/2026-09-09T05-07-35-558Z/`。其后端依赖获取因 Spring 仓库回退缓慢被主动停止，**不是业务编译错误**；见 attempt-note.json。最终运行：`runs/2026-09-09T05-16-36-817Z/`。独立健康检查：`runs/2026-09-09T05-25-48-376Z/summary.json`。原始日志与源码副本留在本地忽略目录，不提交密钥或大体积依赖。

工具版本实测：Maven3.9.16、Temurin21.0.12、Node22.23.2。Windows普通沙箱执行遇到1385登录权限错误，已通过受审核执行入口继续；不是项目代码失败。Maven验证专用设置把Spring仓库映射到Central，依赖版本未变。初次下载与Windows绑定目录I/O耗时较高，数字仅为本机本次运行时长，不宣称性能基准。

修改只涉及开发验证、新测试、package.json只读lint脚本、忽略规则、报告与状态；生产业务代码无修改。[源码补丁](source-changes.patch)保留8个实现文件的可审阅差异（状态、任务契约和报告另见对应文件）。

## A1 建议（本次不执行）

后续授权 A1 时按路由读取 `10-a-minimum.md`。以已核实的新编排正文节点后为评审接入候选，先定义结构化评审契约及最多两轮局部修订、人工处理出口，并验证旧分支与手动改大纲路径是否需要兼容。把 C01/C03 的受众偏离和无依据数字/机构归因变成独立断言；保留固定 Mock 回归，真实文本/图像小样本与费用单列。A0 构建或环境失败不因进入 A1 而自动视为解决。

原有上下文失败分类为 `ENVIRONMENT / EXTERNAL_CONFIGURATION_DEPENDENCY`。实际首个根因是缺少 DashScope API key；不能把未运行到的 MySQL/Redis 初始化描述为已测失败，也不能据此断言生产容器异常。本次未删除原测试、给回归注入真实密钥或放宽断言。前端lint为既有静态检查债务；全套基线入口因此预期仍返回非零，不是“全部验收通过”。
H0探针修正记录：完整运行中的初版20秒就绪窗口未等到Vite ready，仍按设计清理容器；该失败保留在原始运行结果，不归为产品缺陷。根据此证据将就绪预算改为90秒，并新增只接受既有时间戳源码副本的 `dev` 模式。独立复验 `runs/2026-09-09T05-43-18-295Z/` 在41749ms通过HTTP检查（Vite日志ready=40775ms），停止容器退出0。未修改HTTP/HTML断言，未重复真实模型请求，未自动更新内容或视觉基线。记录中的临时端口已关闭，不是可持续访问的预览地址。
测试基线范围：原Java后端只有 `MainApplicationTests.contextLoads`，本次新增 `A0BaselineTest` 的5个动态案例；两个测试类分别运行，原有失败未被排除出基线。前端原package.json没有test/e2e脚本，本次只新增只读lint入口，未声称已有前端单元测试、浏览器交互测试或视觉验收通过。
## 交付结论

A0已完成基线测量与H0验证入口建设；不是A完整闭环或全套测试全绿。最终完整运行退出1：原有contextLoads配置失败、lint失败，以及该次运行旧20秒启动探针的失败均原样保留；启动探针已通过90秒预算的独立复验解决。当前未解决项为原上下文配置依赖及16个现有lint错误（另1警告）。没有修复生产业务、删除失败测试或降低断言。

- [完整命令、退出码、耗时摘要](baseline-run.json)
- [包含启动修正与真实API独立结果的汇总](validation-summary.json)
- [每个固定案例结果](mock-cases.json)
- [保护路径及HEAD核验](verification-meta.json)
- [使用方式](../../tools/dev-harness/README.md)
- [状态交接](../../docs/agent/status.md)

真实文本API只跑一次；生图、真实应用全链路、HTTP鉴权、数据库事务、异步线程与网络SSE、浏览器/视觉仍未验收。现有数据与容器保留，未提交/推送、修改远端或部署。A1仅提供上述建议，未开始实施。