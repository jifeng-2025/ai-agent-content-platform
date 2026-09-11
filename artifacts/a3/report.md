# A3 内容 Runtime：交付与验收报告

日期：2026-09-10。结论：**A3 已完成，在默认 Mock 云边界的隔离环境达到本次验收标准；原有基线并非全绿。可以进入 A4，尚未实施 A4。** Runtime 默认关闭，没有迁移用户库、重启原服务、提交/推送或部署。真实收费 API：**NOT_RUN**。

## 实现范围与入口

沿用 Java/Vue/MySQL/Redis，复用 A2 operation、review、media，没有另建正文版本或人工决策系统。

| 交付 | 实现及关键入口 |
|---|---|
| A3.1 持久恢复 | `RuntimeIntake` 将普通创作事务入队；A2 `ArticleInterventionService` 同事务 enroll；`RuntimeStore`、`RuntimeWorker` 持久认领、15秒租约/2秒心跳、fence、阶段检查点；恢复复用保存结果及修订预算 |
| A3.2 外部调用 | `RuntimeLedger`、`RuntimeExternal`、`RuntimeProvider` 保存稳定 callId、参数摘要、jobId、结果及尝试；查询优先、无法确定则暂停；程序执行时间/次数/图片重试/估算费用限制；`RuntimeControl` 提供取消、查询及明确授权后的重试 |
| A3.3 通知/UI | `RuntimeEventStore`、`RuntimeEvents` 将持久事件与状态同事务提交；SSE游标补发、过期快照、终态关闭；Vue `utils/sse.ts` 去重和持久游标，`ArticleReviewPanel.vue` 展示恢复/取消/预算/不确定和人工控制；创作页通过URL taskId恢复 |
| 迁移 | `sql/add_article_runtime.sql`：复用 article_operation 增量列，新增 article_runtime/article_call/article_event；可重复执行，保留历史 |
| 可重复验证 | `tools/dev-harness/run.mjs` 实际执行 A3 测试并解析 Surefire 数量；`a3-store/live/fault/browser` 分别提供 JVM恢复、真实HTTP、进程故障和浏览器验证 |

完整状态/接口/取消/事件契约见 [a3-contract.md](../../docs/agent/a3-contract.md)。主要状态为 QUEUED → RUNNING/RECOVERING → 人工等待或 COMPLETED；CANCELLED、TIMED_OUT、BUDGET_EXHAUSTED、EXTERNAL_UNCERTAIN 均不会伪装成图文成功。

## 实际验证结果

机器可读统计：[validation.json](validation.json)。**功能 JUnit 共88项、88通过、0失败、0跳过**；依赖预热和断网重跑不重复计数，进程服务夹具也不计入功能测试数。

| 验证 | 实际结果 | 持久证据 |
|---|---|---|
| A0/A1/A2 + A3纯回归 | 74/74：A0 5、A1 51、A2 7、A3 11；A3包含恢复评审2、外部调用6、预算3；两次执行均通过 | [Surefire汇总](evidence/mock/summary.json)及同目录9份测试报告 |
| Spring/MySQL/Redis真实HTTP | 13/13：继承9项A2权限、冲突、人工操作断言，新增4项Runtime创作/取消/预算/不确定与SSE | [A3HttpIT](evidence/http/com.yupi.template.intervention.A3HttpIT.txt) |
| 实际JVM中断/重启 | 1/1：第一JVM提交后SIGKILL，新JVM对同一MySQL认领；并发唯一认领、实际旧fence写入拒绝、版本/次数/期限保持；真实RuntimeWorker处理DONE时不调用生成器 | [A3StoreIT](evidence/store/com.yupi.template.runtime.A3StoreIT.txt)、[进程记录](evidence/store/process-summary.json) |
| 完整进程故障矩阵 | 12项检查通过；3次实际SIGKILL/重启、双Worker暂停/恢复、真实HTTP供应商先成功后丢响应；非纯内存恢复模拟 | [故障时间线与检查](evidence/fault/fault-result.json)、[阶段记录](evidence/fault/process-checkpoints.jsonl)、[供应商持久记录](evidence/fault/provider-state.json) |
| MySQL增量迁移 | A3执行两次，原合成文章、COMPLETED操作和currentVersion=2评审保留，新增3表；专用tmpfs数据库 | [迁移原始结果](evidence/migration/migration-smoke.json) |
| SSE工具回归 | 原4项 + A3 6项通过；此项使用模拟EventSource，与真实浏览器网络验收明确分开 | [原SSE](evidence/http/a1-sse-test.cjs.txt)、[A3 SSE](evidence/http/a3-sse-test.cjs.txt) |
| 浏览器真实联调 | 28项检查通过；桌面1440×1000、手机390×844；8张截图实际逐张查看 | [检查结果](evidence/browser/result.json)、[视觉记录](evidence/browser/visual-review.json) |
| 编译/构建 | 后端package、前端type-check/build通过 | [后端](evidence/mock/backend-package.txt)、[前端](evidence/front/summary.json) |
| 原contextLoads | 1项、1配置错误：隔离配置缺DashScope API key；未改动此测试 | [输出](evidence/existing-context/log.txt) |
| 原lint | 16错误、1警告，沿用基线；未进行全仓修复 | [lint](evidence/front/lint-check.txt) |
| 真实收费云模型/图片 | NOT_RUN；固定Mock响应不能证明真实云质量、账单或生产查询能力 | 不属于默认验收依赖 |

所有验证使用本次隔离MySQL/Redis/HTTP/测试进程，无用户数据卷。最终只剩原4个用户容器，仍为退出状态；用户已确认退出是主动操作，不是故障。专用容器和网络已清理，见 [最终容器状态](evidence/final-containers.txt)；原服务健康探针标NOT_RUN。

## 故障证据与复现

复现命令（仓库根目录；Node路径不在PATH时使用本机Node绝对路径）：

```powershell
node tools/dev-harness/run.mjs a3-mock
node tools/dev-harness/a3-db-smoke.mjs
node tools/dev-harness/a3-store.mjs
node tools/dev-harness/a3-live.mjs start
# 等待输出的live.json对应 target/a2-http-ready.json，failedTests必须为空
node tools/dev-harness/a3-browser.mjs <live.json>
node tools/dev-harness/a3-front.mjs <live.json>
node tools/dev-harness/a3-live.mjs stop <live.json>

$env:A3_PROCESS_ISOLATED='true'
node tools/dev-harness/a3-live.mjs start
Remove-Item Env:A3_PROCESS_ISOLATED
node tools/dev-harness/a3-fault.mjs <进程测试live.json>
node tools/dev-harness/a3-process-clean.mjs <进程测试live.json>
```

完整前置依赖和隔离方式见 [Harness README](../../tools/dev-harness/README.md)。`run.mjs a3`包含原contextLoads/lint，保留其非零退出码。首次启动依赖缓存与Docker镜像；这些不是生产启动/部署命令。

实际故障顺序与UTC时间完整保存在fault-result.json：03:08:27提交QUEUED后杀进程；03:12:14恢复标题；03:12:15再次杀进程后复用已完成标题；03:15:35供应商已保存正文但客户端未收到响应，03:15:37杀进程，重启后查询同一稳定标识，提交数不增加。后续真实双Worker竞争、旧Worker恢复和执行中取消均通过。额外Store测试于03:36:53杀第一JVM、03:37:09启动恢复JVM，03:38:29完成，验证保存版本和预算不清零。

浏览器真实点击“人工确认后重试”时，确认可能重复收费，后端同一run调用计数从2增到3、估算预留从0.2增到0.3；随后真实取消，保留正文/评审。网络层注入一次连接关闭，观察实际带cursor重连；重复事件另由工具测试覆盖。恢复中视觉使用明确的持久fixture，不能替代前述真实进程恢复证据。

截图：[桌面取消](evidence/browser/01-desktop-cancelled.png)、[预算耗尽](evidence/browser/02-desktop-budget.png)、[恢复中](evidence/browser/03-desktop-recovering.png)、[结果不确定](evidence/browser/04-desktop-uncertain.png)、[手机不确定](evidence/browser/05-mobile-uncertain.png)、[人工确认](evidence/browser/06-mobile-retry-confirmation.png)、[手机取消](evidence/browser/07-mobile-cancelled.png)、[旧文章](evidence/browser/08-mobile-old-article.png)。

[console](evidence/browser/console.json)记录1次预期注入的SSE错误；[network](evidence/browser/network.json)记录对应ERR_CONNECTION_CLOSED和2次主动取消的ERR_ABORTED，无额外未解释网络/控制台错误。终态后不再建立连接或继续轮询，页面卸载清理也有回归覆盖。

## 失败分类与修复记录

保留全部原始运行目录，未删测试、降断言或通过扩大业务超时掩盖问题。

- **既有失败**：contextLoads缺模型配置；lint16错1警告。与A3回归失败分开统计。
- **本次已修复代码问题**：前端可选标题/大纲类型映射；SSE游标已追平时未立即发送内容导致响应头不刷新，现立即注释并每15秒心跳；外部步骤过期前置检查，避免超期才启动调用；明确授权图片重试仍扣持久额度。
- **本次已修复测试/夹具问题**：A3外部测试短callId不符合真实SHA标识；正常流程缺stream模型Mock；浏览器旧文章路由指向未创建fixture；重复验收隔离fixture的MySQL跨排序规则JOIN和JSON数组解析。最终测试全部重新通过。
- **故障Harness先前失败**：冷启动注册超时但请求已在后端成功、SQL客户端编码、恢复查询字段错误。保留失败JSON，核对已提交记录后从中断处继续；没有反复注册或增加供应商收费调用。原始文件位于 `runs/2026-09-10T02-59-00-920Z-live/fault-*.json`。
- **权限**：Windows默认沙箱报1385后使用明确审批的项目内操作。一次自动审批拒绝图片重试计数补丁，核实它只计量、不会授权调用后改为显式AUTHORIZED_RETRY条件并获准；没有绕过收费重试授权。无未解决权限阻塞。

## 源码指纹与证据范围

最终文件SHA-256见 [source-fingerprint.json](source-fingerprint.json)，逐文件快照比较见 [source-verification.json](source-verification.json)。基准HEAD为 `09b539f9ace45e40d2c5f57ad1f6cea49c19a603`；A3仍为未提交修改。

最终Mock和HTTP副本的后端、前端业务源码与交付一致；前端env.ts是Harness特意隔离的测试配置。最终Store副本136个Java文件完全一致；前端type/build副本除同一隔离env.ts外一致。

完整进程故障运行使用较早的02:59源码副本；后续加入的SSE响应头心跳、外部过期前置检查、授权图片重试计数、Runtime下阻止未计量的可选AI改纲及前端类型映射，由最新Mock/HTTP/浏览器/构建覆盖。**不宣称全部故障是在最终同一个二进制上重跑**；各次原始指纹独立保留，差异逐项列出。

## 限制、启动与非破坏性回退

已实现且已实测：正常创作和A2人工操作持久调度、检查点复用、fence、取消、次数/重试/估算预算、迁移幂等、游标与GET恢复。未验证：真实收费API、真实供应商异步查询/取消、生产负载/多机长稳/供应商实际费用。当前同步生产接口没有可依赖的查询适配，遇到结果不确定会暂停，不冒称跨系统exactly-once。

运行预算定义为**累计有效执行租约时间**，默认900秒；人工等待/排队/停机超过租约的时间不计入。不是绝对墙钟截止日期。估算预留不是账单上限，实际值未获得为null。供应商不能取消已发请求时仍可能收费，界面已明确提示。

默认 `article.runtime.enabled=false`。如后续另行授权启用，应先在目标副本完成A1/A2/A3增量迁移，开启review-loop、intervention和runtime开关，配置真实云服务及额度。Runtime模式暂时拒绝可选的AI自动改纲，仍支持手动编辑大纲；避免该辅助入口绕开账本。预算错误栏仍显示原始BUDGET_EXHAUSTED代码，中文状态已正确显示，属于小型文案待办。

回退先结束或取消在途Runtime操作，再关闭Runtime开关；保留新增表列、账本、版本和事件，不执行DROP、不清空草稿、不让旧Worker盲目接管Runtime在途操作。本次未对用户服务执行启用或回退。

**A4结论**：可在上述明确边界下进入A4。依赖本次契约/验证入口与快照证据；A4可补最终同一构建的全量故障流水线、生产适配能力探测及长稳/资源压力验证。A3本次已实测与未验证范围不能混为生产SLA，本次未实现A4/B。
