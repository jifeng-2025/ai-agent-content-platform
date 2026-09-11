# A0 / A1 / A2 / H0 开发验证

A0 建立开发验证入口；A1 扩展独立后端评审回归（见本文末）。本入口不实现恢复、H1 或部署。

在仓库根目录使用宿主 Node（本机 Codex 提供）和 Docker：

```powershell
node tools/dev-harness/run.mjs baseline
node tools/dev-harness/run.mjs mock
node tools/dev-harness/run.mjs health
node tools/dev-harness/real-smoke.mjs
```

`baseline` 串行执行 JDK/Maven 版本、后端 package、Mock、原有 contextLoads、Node 版本、npm ci、临时 Vite 启动健康检查、类型检查、完整 build、只读 lint，以及已部署前后端 GET 健康检查。`mock` 仅后端准备与固定回归（仍附带只读健康检查）；`health` 不构建。任一执行检查失败会退出 1，不把已知基线失败变成绿色；真实 API 的 NOT_RUN 单独记录，不算通过。

每次运行创建 `artifacts/a0/runs/<UTC时间>/summary.json`，包含命令、退出码/HTTP 状态、耗时及日志。源码副本在 `tools/dev-harness/.work/<时间>`，Maven 缓存在 `.cache/m2`。这些目录已忽略提交，保留本地排错证据，不自动删除。首次运行需联网拉取固定 digest 的镜像、Maven 与 npm 依赖；资源约束为单容器 2 CPU / 3 GiB，单命令最多 10 分钟，超时移除本次命名的工具容器。

隔离规则：仅复制 pom.xml、src、frontend 中 Git 跟踪及未忽略的文件，排除 .env、application-local、env.ts；在副本生成 `API_BASE_URL='/api'`。不挂载现有 MySQL/Redis 卷、Docker socket、宿主凭据或配置。Mock 与原有 contextLoads 在 `--network=none` 下执行；测试依赖首次由纯 Mock 测试在线预热（无 Spring 上下文/真实 I/O），随后断网复跑。原有 contextLoads 没有独立数据库、Redis、模型配置，失败应如实记录。禁止用 production 配置运行它来制造通过。

五个案例唯一来源为 `src/test/resources/a0/cases.json`：C01–C03 正常三阶段，C04 标题 JSON 错误，C05 模型异常。真实 Controller、ArticleAsyncService、StateGraph、标题/大纲/正文/配图分析/合成节点执行；模型、ParallelImageGenerator、ArticleService 持久化边界、UserService 与 SSE 发送器为 Mock。断言标题/大纲保存、配置分流、配图替换、完成/失败状态、SSE 事件以及模型调用次数。固定响应在 A0BaselineTest 中维护，不自动更新断言或样例。

局限：Controller 为直接方法调用，不包含 Spring HTTP 映射、权限 AOP、真实事务、线程池代理或网络 SSE；图片工具不实际访问图库/生图；三个成功案例的固定正文仅用于编排契约，不是受众质量评测。案例的 audience/reviewFocus 为后续 A1 评测输入，不代表当前已实现质量评分。旧 ArticleAgentService 分流仅做无调用断言，旧分支内部未验证。

真实文本 API 冒烟独立入口：

```powershell
# A0_DASHSCOPE_API_KEY 由当前会话环境提供；不要写入脚本、提交或日志
node tools/dev-harness/real-smoke.mjs --execute
```

固定请求 qwen-plus，一次文本调用、最多 256 输出 token、45 秒超时、无自动重试；缺少专用环境变量退出 2，默认调用仅预检（0 次调用）。只记录状态、模型、耗时、token usage；不落盘响应正文和密钥。费用需按供应商账单核实，不猜价格。它验证供应商文本契约，不证明应用全流程、真实配图或内容质量。真实生图本次未执行，不混入 Mock 通过率。

本入口不会启动/重启已有 Compose 服务、创建用户记录、消耗站内配额、迁移数据库、推送 Git 或部署服务器。现有健康检查只证明被访问进程可响应，不能证明容器镜像等于当前源码。

本次依赖获取使用 `maven-settings.xml` 将 POM 中两个 Spring 仓库映射到 Central，仅影响验证工具；没有改写 pom 或依赖版本。源码清单保存 SHA-256；测试文本报告复制到运行产物目录。临时 Vite 仅绑定 127.0.0.1 随机端口，健康检查后移除本次容器。它不连接数据库，也不证明浏览器功能通过。

2026-09-09 单独真实文本实测见 `artifacts/a0/real-api-result.json`：本次任务显式将已有 DashScope 配置传入专用进程环境，完成后清除；runner 本身仍不读取 .env。一次 HTTP 200、98 token、0 次生图，不存储响应正文。运行 baseline 时出现 real-api NOT_RUN 指“此条 runner 未调用真实接口”，须与单独真实 API 产物分别解读。
单独重验开发启动可用 `node tools/dev-harness/run.mjs dev <既有源码副本绝对路径>`，路径取先前 summary.json 的 work 字段。该模式只接受 `.work` 下已安装依赖的时间戳目录，复用该次源码/依赖，不重新安装或构建。Vite就绪预算为90秒，仍要求HTTP成功且响应包含HTML；超时记录FAIL并清理本次容器。首次20秒探针未就绪的证据保留，增加冷启动预算不改变功能断言。
## A1 扩展入口

```powershell
node tools/dev-harness/run.mjs a1-mock
node tools/dev-harness/run.mjs a1
node tools/dev-harness/a1-db-smoke.mjs
```

`a1-mock` 复用 A0 隔离打包及断网回归，但选择器为 `A0BaselineTest,A1*Test`，实际包含新增 Loop、编排集成和持久化/权限测试。`a1` 另执行原有 contextLoads、前端安装/类型/build/lint、4 个模拟 EventSource 兼容测试及 Vite 启动。原有 A0 五例保持原文件与断言，原 `baseline/mock` 命令语义保留。A1 的 summary、文本/XML测试报告和测试生成的 a1-example.json 在 `artifacts/a1/runs/<UTC时间>`。

`a1-db-smoke.mjs` 使用本机已安装的 mysql:8.0 镜像（记录 image ID）、新建命名容器、`--network=none`、tmpfs 数据盘，执行新增表两次、upsert、rollback 与未涉及合成行保持校验，最后只删除该次容器。无用户库连接、无已有数据卷挂载；这是 SQL 冒烟，不等于真实 Spring 事务端到端测试。A1 Mock 持久化测试验证服务事务边界与权限调用，未启动完整 Spring 上下文。

A1 真 API 默认 NOT_RUN；A0 的一次真实文本供应商冒烟不证明 A1 Loop 已经过真实模型验收。详见 [A1 契约](../../docs/agent/a1-contract.md)。

## A2 扩展入口

```powershell
node tools/dev-harness/run.mjs a2-mock
node tools/dev-harness/run.mjs a2
node tools/dev-harness/a2-live.mjs start
# 等待输出目录中的 .work/.../target/a2-http-ready.json；failedTests必须为空
node tools/dev-harness/a2-browser.mjs <live.json绝对路径>
node tools/dev-harness/a2-live.mjs stop <live.json绝对路径>
```

`a2-mock`实际选择`A0BaselineTest,A1*Test,A2*Test`，保持旧测试不变；`a2`另跑原contextLoads、SSE兼容、类型/build/lint。A2HttpIT单独由live入口执行，不混成纯Mock单测。默认不调用真实模型或图片API。

live入口依赖本机已缓存Docker镜像及完成安装的隔离前端依赖副本，使用新MySQL/Redis tmpfs、internal网络，无用户数据卷；只有测试前端连接bridge并绑定127.0.0.1随机端口，代理隔离后端。执行真实注册登录、控制器、Spring事务、异步代理和MySQL迁移，云模型/图片/COS边界为固定Mock。A1/A2迁移应用两次且检查既有合成行保留。测试后创建4个浏览器固定案例并最多等30分钟；stop解除等待、收集Surefire、日志后仅清理本次容器网络。

浏览器入口使用已安装Chrome及独立profile，Node24内置WebSocket驱动CDP，不读取用户浏览器数据。实际操作桌面1440×1000与手机390×844、真实后端登录和人工操作；输出截图、console、network、result。检查截图内容后再报告视觉通过。测试API返回固定图片、不会证明真实云端质量。无Chrome时不得将浏览器步骤标为通过。

中断启动时只清理输出prefix对应的测试容器/network，不删除其他容器；保留源码副本与失败日志。最终范围、启动/回退及结果见 [A2报告](../../artifacts/a2/report.md) 与 [A2契约](../../docs/agent/a2-contract.md)。

最终前端可复用已完成的隔离依赖执行 `node tools/dev-harness/a2-front.mjs <live.json>`，新建源码快照、断网执行 type-check/build/lint，不重新下载依赖。`node tools/dev-harness/a2-final-backend.mjs <live.json>` 逐文件验证当前后端源码与运行副本一致，再断网重跑A0/A1/A2纯回归及package，独立执行原contextLoads；该入口保留其失败退出码，不将既有失败忽略。

## A3 扩展入口（默认云边界 Mock）

```powershell
node tools/dev-harness/run.mjs a3-mock
node tools/dev-harness/a3-db-smoke.mjs
node tools/dev-harness/a3-store.mjs
node tools/dev-harness/a3-live.mjs start
# live.json 路径由启动命令输出；等待 target/a2-http-ready.json 且 failedTests=[]
node tools/dev-harness/a3-browser.mjs <live.json>
node tools/dev-harness/a3-front.mjs <live.json>
node tools/dev-harness/a3-live.mjs stop <live.json>
```

`a3-mock`实际运行 A0BaselineTest、A1*Test、A2*Test、A3*Test，并从 Surefire XML 汇总实际测试数；未执行 A3 用例时直接失败。原用户服务已由用户主动停止，A3 模式把原服务健康探针记为 NOT_RUN，不自动启动原容器；其余模式原语义保持。`a3`另包含已有 contextLoads、前端构建/lint与SSE工具测试，已知失败仍保留非零退出码。

`a3-db-smoke`使用本次 MySQL tmpfs容器执行完整前置迁移、两次A3增量迁移及历史行保留断言。`a3-store`启动第一 JVM 提交 QUEUED/检查点/预算后在明确屏障 SIGKILL，另一个 JVM 连接同一独立 MySQL 验证真实 Worker 复用、并发认领、旧 fence 写入拒绝、预算/期限/版本保持。它不冒充完整 HTTP 验收。

`a3-live`复用 A2 的独立 MySQL/Redis/HTTP环境；A3HttpIT继承原9项A2断言并新增正常创作、取消、预算与SSE/不确定调用测试。所有模型、图片、COS为Mock。原A2测试不删除、不覆盖。`a3-browser`是真实Chrome/CDP、真实后端API；恢复状态的视觉用例使用明确标注的持久测试fixture，真正进程恢复另由故障入口证明。SSE中断在浏览器网络层发生，重复事件单测则明确属于模拟EventSource。

真实进程与外部成功丢响应故障入口：

```powershell
$env:A3_PROCESS_ISOLATED='true'
node tools/dev-harness/a3-live.mjs start
Remove-Item Env:A3_PROCESS_ISOLATED
node tools/dev-harness/a3-fault.mjs <本次live.json>
node tools/dev-harness/a3-process-clean.mjs <本次live.json>
```

该入口首次延迟派发以制造“事务已提交未派发”，随后实际SIGKILL/启动新应用容器；保留专用MySQL/Redis和独立HTTP Mock供应商。供应商先持久生成结果、故意不返回；应用重启后通过稳定标识查询，核对真实提交次数。双Worker通过暂停旧进程、租约过期接管、恢复旧进程验证迟到保护。最后进行执行中取消与终态SSE读取。`--resume`仅供保留失败记录后的本次专用环境继续未完成阶段，完整首次验收使用默认入口。

清理仅接受本次 `a3-live-*` 前缀，专用数据用tmpfs，不挂载用户数据卷。原用户容器不启动/停止/迁移。真实收费API始终单独标记NOT_RUN，所有默认命令不读取用户.env或云密钥。A3运行原始产物位于artifacts/a3/runs（Git忽略）；交付摘要、指纹和证据索引在artifacts/a3。
## A4 候选版总验收（H0，不是H1调度器）

```powershell
node tools/dev-harness/a4-candidate.mjs freeze
node tools/dev-harness/a4-run.mjs
# 实际查看本次截图、导出和图文，再记录视觉结果
node tools/dev-harness/a4-verify-evidence.mjs
```

`a4-run`串行调用固定响应回归、前端构建、真实JVM恢复、HTTP/浏览器、完整进程故障和两种Compose安装；Maven/Node每项最多2CPU、3GB。测试使用本机已缓存的隔离依赖。首次依赖准备沿用A0入口，不自动启动原容器。`a4-verify-evidence`读取本次 `artifacts/a4/acceptance-runs.json`，核对实际测试数量、每个快照源码、Java业务class字节与打包构建；它不是以旧报告代替重新执行测试。

`a4-compose start <backend-work> <frontend-work> fresh|upgrade`基于原Compose解析结果创建专用项目，实际运行打包JAR/前端dist；单独插件目录只有A4测试HTTP供应商适配器，不加载其他测试配置。镜像中旧前端目录由本次构建dist只读覆盖，基础Nginx镜像ID及JAR指纹记录在live.json。测试前端额外连接独立UI网络；MySQL/Redis/backend保持内部网络。等待MySQL TCP就绪后执行迁移，避免临时初始化服务器假就绪。

`a4-install-check <live.json>`验证真实应用健康、登录会话、旧数据和关闭/重新开启Runtime的非破坏性回退。`a4-compose stop <live.json>`只删除明确的本次测试项目及其专用卷，不触及用户卷。生产回退不得清卷。

`a4-browser <live.json>`从正常/create页面创建五个主题，覆盖标题/大纲/正文/修订/人工操作/单图重试/导出；`a4-inspect <live.json> <snapshots.json>`复查已保存版本差异和配图。使用真实Chrome/CDP、独立profile，不读取用户浏览器资料。`a4-context <backend-work>`单独保留原contextLoads失败。工具不调用收费API；授权方案见A4产物，未授权保持NOT_RUN。

本地存储修订后，`a3-mock`也执行 `A4*Test`（含实际 Spring 配置绑定），当前新增供应商测试后，最终门禁将按实际JUnit结果核对；旧数量仅属于历史候选。`a3-store`额外在磁盘保存失败后中断JVM，验证数据库留存图片复用且模型不再调用。

`a4-local-http <live.json> <snapshots.json>`检查鉴权图片/ZIP、越权/路径穿越、零COS配置及专用后端容器重建后字节一致；`a4-offline-browser <live.json> <local-storage/result.json>`打开实际解压的ZIP并阻断网络；`a4-default-browser <live.json>`验证普通账户不选择配图方式也走默认DEMO占位/本地存储。云边界仍为固定响应，不能证明真实图文质量。以上步骤已纳入 `a4-run` 串行入口。

`a4-collect.mjs`仅在工程门禁通过后收集本轮合成数据证据到 `artifacts/a4/local-storage-evidence`。真实下载文件、ZIP、完整服务日志、依赖和构建产物留在Git忽略目录；必须实际查看新截图再填写视觉检查结果。

仅修复验收工具、业务候选未变化时，可在首段已真实通过并保存 `acceptance-runs.json` 后使用 `node tools/dev-harness/a4-run.mjs --install-only` 重跑完整安装/浏览器尾段。该模式校验sourceId，最终仍核对每份源码/构建和全部测试结果；不得用于跳过新业务版本回归。必须保留首轮失败及两段命令记录，不能宣称失败命令本身通过。

双供应商开发验证：`node tools/dev-harness/run.mjs a3-providers`只选择A4ImageProviderTest，并检查实际执行数量；测试使用官方协议固定响应、本机隔离HTTP和假配置。`a3-mock`仍保留全部A0–A4回归。新增SQL已纳入a3-live/a3-store/a4-compose的可重复迁移。最终稳定候选再freeze并运行a4-run，不因文档变化重跑全门禁。

A4同源码中断恢复：`node tools/dev-harness/a4-run.mjs --resume-http` 只在当前 candidate 与 acceptance-runs 的 sourceId一致、前置mock/front/store已完整通过时使用，从HTTP重跑其后全部门禁；`--install-only`从安装开始。业务源码改变必须重新freeze及完整运行，不能借恢复跳过受影响测试。导航就绪检查同时匹配目标URL与readyState，仍使用原120秒导航/30秒操作限制。
