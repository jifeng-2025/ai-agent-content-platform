# A4 最小闭环总验收与 v0.1.0 候选准备

日期：2026-09-10。结论：**工程候选版；真实收费 API 为 NOT_RUN，A4 不能标记全部通过。** 最终执行状态以 [validation.json](validation.json) 和 [final-gate.json](final-gate.json) 为准。未提交、推送、创建 Release、部署或迁移用户数据库；B/H1/D1 未实施。

## 候选版本与范围

- 固定源码 ID：`07d8b1f9cd71d44c65fd58d72693a7447400b9de5533d35468da100510450082`，基于 HEAD `09b539f9ace45e40d2c5f57ad1f6cea49c19a603` 加现有未提交修改。
- 实际安装 JAR SHA256：`fbce0b0667ef50e88f4263060eaaac8dd0d24a06a6de17f30885c00bc1de698f`。内部 Maven 版本仍为 `0.0.1-SNAPSHOT`；没有创建 v0.1.0 tag。
- [candidate.json](candidate.json) 是业务/测试源码逐文件 SHA256 清单；[source-verification.json](source-verification.json) 对照各实际工作快照；[build-fingerprints.json](build-fingerprints.json) 对照业务 class 字节、JAR 解包 class 与 Vue dist。不是将旧 A3 故障证据拼入新版本。
- A4 未修改 A0–A3 业务代码。新增测试专用 `A4MockProviderConfig` 在冻结前纳入测试快照；实际 JAR 启动时通过独立测试插件接入 HTTP Mock，插件不进入产品 JAR。新增候选、Compose、浏览器、审计与汇总工具，修复测试启动/浏览器同步问题，整理发布文档。
- 环境差异明确记录：独立 MySQL/Redis；测试 profile；固定响应 HTTP 供应商；前端 `/api` 相对地址；首次故障派发延迟；额外标注 A4 MOCK 的 SVG。新安装、升级及页面端到端均使用同一个实际 JAR 和 dist，非 JUnit 启动应用。

## 已实测门禁

| 项目 | 实际数量 / 结果 | 证据 |
|---|---|---|
| A0/A1/A2/A3 固定响应 Java | 74/74；预热和离线重跑均通过，统计不重复累加 | evidence/mock/summary.json |
| MySQL/Redis/HTTP 集成 | 13/13，包含继承的 9 项 A2 与 4 项 A3 | evidence/http/ |
| 真 JVM 中断与 Store 恢复 | 1/1；持久预算/版本/检查点、并发认领和 fence | evidence/store/ |
| 完整 HTTP 进程故障 | 独立 SIGKILL/重启、两个 Worker、供应商计数；最终结果见 validation.json | evidence/fault/ |
| SSE 工具回归 | 原 4 项 + A3 6 项通过；模拟 EventSource，另有真实网络验收 | evidence/sse/ |
| Vue 类型 / 构建 | 通过 | evidence/front/ |
| 正常页面五主题闭环 | 67 项检查通过 | evidence/browser/ |
| Runtime 异常状态浏览器 | 30 项检查通过 | evidence/runtimeBrowser/ |
| 版本差异、配图视觉复查 | 9 项检查通过 | evidence/visual/ |
| 正常页面触发占位降级 | 17 项检查通过 | evidence/degraded/ |
| 新安装 / 升级 / 非破坏回退 | 5 + 6 项检查通过；三份增量 SQL 各执行两次 | evidence/fresh/、evidence/upgrade/ |
| 原 contextLoads | 1 项执行、1 error：缺 DashScope 配置；既有失败 | evidence/context/ |
| 原 lint | 16 errors / 1 warning；既有失败，未删测试或放宽规则 | evidence/front/ |
| 最终真实收费文本及生图 | **NOT_RUN，尚无授权**；不引用 A0 历史冒烟替代 | paid-smoke-plan.md |

Java 功能测试合计 **88/88**，不把 HTTP 断言、浏览器检查或故障检查计作更多 JUnit 测试。contextLoads 独立执行并保留非零退出码。浏览器合计 **123 项检查、33 张截图实际查看**，桌面 1440×1000、移动 390×844。自动化断言和人工截图判断分开，详情见 [visual-review.json](visual-review.json)。

## 全链路与五案例

实际 Chrome 从 `/create` 开始创建五个 A0 主题，经过标题选择（刷新恢复）、大纲确认、正文、评审、配图、合成与详情。C01 一次局部修订、C03 两次后待人工并人工接受、C04 保存新版本后重新评审、C05 图片失败保留正文再单图重试；C02 正常完成并实际下载 Markdown。补充第六个降级来源用例，后端标记 DEGRADED、实际来源 PLACEHOLDER，页面有明确警告。

[five-cases.md](five-cases.md) / [five-cases.json](five-cases.json) 保存 baseline 与当前结果、版本和任务 ID。原 A0 五份固定 fixture 原样回归 5/5；页面验收复用五个主题并增加固定故障响应，统一使用初学者补充要求，其提示设置并非与 A0 fixture 完全相同。**这些是功能比较，不是实际生成质量提升证据。**

差异截图只标出被修订的 p2，p1/p3 保持；人工接受保留原问题和 v2；编辑重评追加版本/新一轮预算；单图重试只增加目标槽位尝试。真实 HTTP 测试同时保留越权、重复提交、版本冲突、旧文章、Runtime 关闭路径保护。

SSE 断网在浏览器网络层注入，重连携带游标，GET 快照兜底；终态和页面卸载清理另有工具断言。恢复中的视觉状态使用持久 fixture，**实际恢复由独立进程故障测试证明**。取消、预算耗尽、不确定结果没有显示为成功。下载文件确含标题、正文和图片 URL；测试 URL 随隔离环境清理失效，不是自包含离线图片包。

## 故障与失败分类

完整故障时间线、每次 SIGKILL、租约接管与取消请求见 `evidence/fault/fault-result.json`；供应商真实 HTTP 收到的稳定请求、submitCount/queryCount 见 `provider-state.json`。Store 测试另用两个 JVM 验证修订/预算/期限持久性。支持查询的本地供应商只提交一次后查询恢复；生产同步云适配器无可靠查询/取消时暂停不确定结果，不宣称跨供应商 exactly-once。

本轮曾遇到并修复的**验证工具问题**：MySQL 初始化临时服务器被误判就绪（改 TCP 检查）；产品 JAR 插件目录误加载其他测试配置（缩小至 A4 适配器）；内部网络无有效宿主端口（新增专用 UI 网络）；页面控件选择与加载时序；冷启动认证未初始化导致首次注册超时（只添加只读就绪探测，业务请求仍为 15 秒）。初始失败日志保留，不把失败运行计为通过。未删除测试、降低业务断言、扩大业务超时或更新视觉基线。

控制台/网络：正常闭环及降级无 console error；导航引发的 canceled ERR_ABORTED 是 SSE 清理。Runtime 浏览器有一次刻意注入断连导致的错误，已在对应 JSON 标注。没有未解释的业务 HTTP 失败。轻微 UI 遗留：大纲页侧栏仍可能显示标题步骤；部分英文原因码；前一步成功 toast 可短暂覆盖完成页面。主操作/状态/移动宽度验收通过，未声称像素完美。

## 可安装、升级、启动和回退

`a4-compose.mjs` 解析现有 Compose，创建独立项目/容器名/端口/命名卷；MySQL/Redis/backend 使用专用内部网络。实际产品 MainApplication JAR 启动，前端使用本次 dist 覆盖缓存 Nginx 基础镜像中的旧目录；镜像 ID 留在 live.json。**验证的是实际应用产物和 Compose 隔离安装，不声称本轮重建了全部产品 Dockerfile 或验证了无缓存下载。**

新安装从空测试卷启动；升级先放入合成旧文章、A1 review v2、A2 已完成 operation、intervention revision3/media，再执行 A3。A1/A2/A3 SQL 均重复执行验证，旧数据保留。实际应用健康、注册/登录、Redis 会话跨重启均通过。关闭 Runtime、重启同一测试 JAR，文章数量保持，再开启；无删表/删列回退。

产品快速启动与配置见根 README。隔离复现：

```powershell
node tools/dev-harness/a4-candidate.mjs verify
node tools/dev-harness/a4-run.mjs
node tools/dev-harness/a4-verify-evidence.mjs
```

统一入口串行执行、Maven/Node 上限 2 CPU/3GB，适配 16GB；双 Worker 故障暂时需要两个 JVM。本轮实际逐项运行入口组成命令，最后汇总门禁；未声称已经额外完整执行一次新 wrapper。具体命令、退出码、耗时和路径见 [validation.json](validation.json) 及各结果 JSON；部分前端/浏览器工具未记录自身墙钟耗时，标记未记录，不能以测试内部时间冒充整个安装耗时。预热构建曾有受限并行，后续统一入口按序运行。

测试清理仅删除本次专用环境。原用户四容器退出是用户主动操作，不是本轮故障，没有启动/重启。生产非破坏回退：先处理在途任务，关闭新开关、保留新增表列和草稿；不能用 `down -v` 操作用户数据。容器镜像/JAR 历史回退及数据库备份恢复需另行授权验证。

## 发布与提交准备

README、CHANGELOG、[候选发布说明](release-notes-v0.1.0.md) 已整理，上游署名保留；origin 应为 jifeng-2025/ai-agent-content-platform，upstream 只关联。[submission-files.txt](submission-files.txt) 是明确待审提交列表（包含原有未提交 A3 + 本次 A4），[submission-manifest.json](submission-manifest.json) 含 SHA256；列表已显式包含三个自生成审计文件，这三者不做递归自哈希。未 stage/commit。

[privacy-audit.json](privacy-audit.json) 检查密钥模式、意外缓存/依赖/数据目录和大文件；仅含隔离合成数据，测试占位密码与本地路径可见，未读取用户 .env/无关密钥。模式扫描不是绝对无泄漏证明，提交前仍应人工检查差异。原始 runs、.work、依赖、JAR/dist、用户卷不进入提交；精选日志/截图保留于 evidence。

**A4 尚未全部通过**：最终版本真实收费文本/图像冒烟和实际图文匹配仍 NOT_RUN，需用户授权；根目录缺独立 LICENSE 文件，正式分发前需核实上游适用授权。A 的工程功能闭环已有 Mock/真实基础设施证据，不能据此宣称真实生成验收完成。可交付本地 v0.1.0 工程候选材料，暂不标记正式 v0.1.0 发布。B 研究引用/RAG/偏好/MCP、H1 自动开发 Harness、D1 CI/GHCR/服务器部署均未实现。

A4收尾复核（2026-09-10）：工程门禁保持 ENGINEERING_PASS；本轮未改业务代码。真实API NOT_RUN：缺Gemini/COS配置及收费授权；分发授权 UNRESOLVED。见 [许可核查](license-review.md)、[配置状态](config-readiness.json)、[修订方案](paid-smoke-plan.md)。既往G0已经推送，本文“未提交/推送”仅指当前A3/A4工作树。本次仅作候选准备。
