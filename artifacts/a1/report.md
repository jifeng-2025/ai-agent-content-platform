# A1 后端结构化评审与有界修订交付

日期：2026-09-09。范围仅 A1；选择 B、先完成 A 的既定决策不变。默认关闭新 Loop，未推送、未部署、未重启已有四个容器，也未对用户库执行迁移或测试。

## 修改与实际路径

`ArticleController.confirmOutline → ArticleService.confirmOutline（保存确认大纲）→ ArticleAsyncService.executePhase3 → ArticleAgentOrchestrator.executePhase3_GenerateContent → ContentGeneratorAgent → ArticleReviewLoop → ImageAnalyzerAgent → ParallelImageGenerator → ContentMergerAgent`。

原编排开关为 false 时继续 ArticleAgentService；新 review-loop 开关为 false 时继续原 Writer/图片 StateGraph。新增受众上下文通过既有 userDescription 传递，来源是确认标题请求及文章记录；并未把 A0 fixture.audience 当成新增生产字段。

主要新增代码为 agent/review 的严格 JSON 校验、规则与段落补丁、模型调用边界和程序有界循环，以及 ReviewResult/ReviewTrace。正文 v0 最多修订至 v1/v2；无法局部修复、明确缺证据、无效结构、超时、重复问题/无变化均有待人工出口。PASS 后才执行原配图和合成，输入为最终修订正文。

必要集成包括 saveReviewProgress 的事务草稿/版本保存、独立 article_review 表、文章 NEEDS_REVIEW 状态与 phase、认证读取接口、先保存后发送的 REVIEW_UPDATED/NEEDS_REVIEW 事件。SSE 推送异常不覆盖已经持久化的评审状态。待人工直接结束 SSE，绕过 saveArticleContent/COMPLETED/ALL_COMPLETE。前端仅增加终止/提示/现有详情页标签，未建设 A2 评审界面。

完整字段与启用约束见 [A1/A2 契约](../../docs/agent/a1-contract.md)。迁移是独立的 [add_article_review.sql](../../sql/add_article_review.sql)，需部署前明确执行；本次没有对现有库执行它。

## 可复现案例

运行 `node tools/dev-harness/run.mjs a1-mock`，执行 `A1ReviewLoopTest.oneRevisionAndReproducibleTrace`，生成运行目录下 `a1-example.json`。

| 阶段 | 固定输入/结果 |
|---|---|
| 初稿 v0 | `## 开始行动`，空行分段：`选择任务。`、`记录感受。` |
| 评审 | REVISE；AUDIENCE / ERROR / p2，建议局部改写 |
| 修订 v1 | p2 改为 `选择一个日常小任务，先完成第一步。`；标题、p3、空行分隔保持原样 |
| 再评审 | PASS，currentVersion=1，versions 同时保留 v0 的问题和 v1 的正文/结论 |
| 集成测试 | 另以固定句 `选择一个日常小任务。` 验证配图分析输入和最终合成确实使用修订稿 |

这是一条固定响应编排回归，不是模型真实质量或事实正确率评测。另有缺证据直接转人工、v2 仍失败、重复问题、非法补丁/评审、超时和存储失败案例。

## 验证证据

- 初次隔离迭代：`runs/2026-09-09T06-32-43-922Z`，包含较早源码快照；不以其代替最终源码验收。
- A1 全量入口（最后 SSE 异常隔离补丁前）：`runs/2026-09-09T06-40-28-463Z`。命令和源码 SHA-256 见 summary.json、source-manifest.json；SSE 兼容 4/4、Vite 启动、类型检查、完整 build、健康 GET 均通过；contextLoads 与 lint 复现既有失败，因此完整入口按设计退出 1。
- 最终后端回归：`runs/2026-09-09T06-45-45-620Z`，包含最后 SSE 推送失败保护及其新增用例；源码编译/打包通过；56 例测试在线依赖预热和断网回归各通过一次，零失败/错误/跳过。全量入口的前端源码与最终源码一致。
- 最终 Java 数量：A0BaselineTest 5 + A1ReviewLoopTest 37 + A1IntegrationTest 9 + A1PersistenceTest 5 = **56**（A1 新增 51）。两次运行不算 112 个独立用例。详见 [机器统计](validation.json)。
- A0 原测试与 fixture 哈希未变：[保留证据](a0-preservation.json)。[可读版本轨迹](example.json)来自最终测试产物。
- SQL 冒烟：`runs/2026-09-09T06-35-21-313Z/migration-smoke.json`，PASS。新 MySQL 容器，无网络、tmpfs 数据盘，重复执行新增表、upsert、rollback、无关合成记录保持断言均通过；容器已清理。不是生产迁移或真实 Spring 事务集成测试。
- A1 真实 API：NOT_RUN。没有使用凭据、消耗真实模型或生图配额；A0 历史真实文本冒烟不作为 A1 验收证据。

| 验证项 | 最终结果 | 分类 |
|---|---|---|
| 后端 package | PASS | 最后补丁源码已打包 |
| Java 固定回归 | 56/56，两次；失败/错误/跳过均 0 | A0 5 + A1 51 |
| 前端 SSE 兼容 | 4/4 | 真实 sse.ts，模拟 EventSource |
| 类型检查 / 完整 build | PASS / PASS | 未改变依赖版本 |
| 临时 Vite / 原容器健康 GET | PASS / PASS | Vite 约 51 秒，原容器不代表新源码已部署 |
| 隔离 MySQL SQL 冒烟 | PASS | 无用户数据卷、无网络，测试容器已清理 |
| 原有 contextLoads | 1 个测试、1 个 Error | 缺 DashScope API key，与 A0 相同 |
| 原有 lint | 16 errors、1 warning | 17 条具体诊断与 A0 相同，仅行号可能移动 |
| A1 真实 API / 真实生图 | NOT_RUN / NOT_RUN | 不计入 Mock 成功 |

[具体 lint 对比](lint-comparison.json)按文件、严重性、消息、规则核对，无新增诊断。所有测试日志保留，不将已知失败转换为通过。最后后端补丁与前端分别对应上述两次运行，源码哈希一致性见 validation.json；源码/测试未在验证后更改。

## 已知限制与下一步

A0 既有 contextLoads 缺模型配置以及 lint 16 错误、1 警告需与 A1 新增回归分别列出，不能宣称全仓通过。Mock 使用真实 Java 节点和异步服务方法，持久化边界/模型/图片/SSE 发送器为模拟；不证明真实 Spring HTTP、事务代理、异步线程池或真实 SSE 网络传输。新增 SQL 冒烟单独验证语法和存储行为。前端兼容测试使用模拟 EventSource，不是浏览器截图验收。

全篇结构/篇幅问题保守转人工；问题类型/段落/严重性集合未减少也提前停止。规则只覆盖部分可确定风险，不能穷尽事实真实性。新路径 Writer 失败沿用 FAILED；未保存半段 token，不实现恢复调度。

A2 建议读取 article.status/phase 和 ReviewTrace，显示修订轮次、问题与版本差异，明确事实未核查，并处理 SSE 丢失后的 GET 查询。人工接受/修改重试接口尚未提供，不应仅在前端伪造恢复。A3 再实现恢复、幂等和事件重放；本次没有进入这些工作。
