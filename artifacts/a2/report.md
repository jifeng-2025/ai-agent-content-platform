# A2 交付与验证报告

2026-09-09。**A2 已达到本次验收范围；全仓基线仍非全绿。** 已完成评审可视化、真实人工接受/编辑重评/单图重试、必要持久化与服务端并发防护。独立开关默认关闭。未进入 A3/B，未推送、部署、迁移用户库或重启用户服务。

机器可读结论与源码校验：[validation.json](validation.json)。本次保留原有未提交 A0/A1 文档、测试、工具；A0 五例fixture及A0/A1四个测试类SHA-256与A1验收副本一致。最终后端/前端源码与验收副本逐文件比对，差异0。

## 实现范围与入口

| 改动 | 关键文件 |
|---|---|
| 阶段、问题、停止原因、全局版本/轮次、历史正文与行差异、人工决定 | [ArticleReviewPanel.vue](../../frontend/src/components/ArticleReviewPanel.vue) |
| 持久GET恢复、SSE提示+轮询、卸载/路由切换取消请求；Markdown允许列表 | [ArticleDetailPage.vue](../../frontend/src/pages/article/ArticleDetailPage.vue)、[interventions.ts](../../frontend/src/api/interventions.ts)、[safeMarkdown.ts](../../frontend/src/utils/safeMarkdown.ts) |
| 真实登录、归属、状态/预期版本校验、文章锁、请求摘要去重 | [ArticleInterventionController](../../src/main/java/com/yupi/template/controller/ArticleInterventionController.java)、[ArticleInterventionService](../../src/main/java/com/yupi/template/service/ArticleInterventionService.java) |
| 异步唯一认领、接受后继续/编辑新轮/媒体失败出口 | [ArticleInterventionWorker](../../src/main/java/com/yupi/template/service/ArticleInterventionWorker.java) |
| 每轮最多2次、全局版本递增、保留原问题/决定 | [ArticleReviewLoop](../../src/main/java/com/yupi/template/agent/review/ArticleReviewLoop.java)、[ReviewTrace](../../src/main/java/com/yupi/template/model/dto/article/ReviewTrace.java) |
| 只重试目标图、正文不重写、保留其他图、显式降级/失败 | [ArticleMediaProcessor](../../src/main/java/com/yupi/template/service/ArticleMediaProcessor.java)、[ArticleMediaStore](../../src/main/java/com/yupi/template/repository/ArticleMediaStore.java) |
| JDBC复用Flex事务连接，避免原始连接绕过行锁/回滚 | [ArticleJdbcConfig](../../src/main/java/com/yupi/template/config/ArticleJdbcConfig.java) |
| 新表及正文容量增量迁移 | [add_article_intervention.sql](../../sql/add_article_intervention.sql) |

实际链路：Controller → transactional submit → async Worker → 原评审Loop或媒体Processor → MySQL保存 → SSE终态。A1正常通过路径在A2开启时也使用同一媒体Processor；ArticleAsyncService识别mediaHandled，避免覆盖终态。NEEDS_REVIEW仍停止，不生图、不发送ALL_COMPLETE。

人工接受追加userId/时间/备注，保留原NEEDS_REVIEW评审，trace变HUMAN_ACCEPTED；PASS仅指文本评审通过，COMPLETED仅指图文任务完成，均不代表事实核查。编辑追加全局版本并定义新的roundStartVersion，重复请求不会重新建立预算。媒体模板保留审定正文，忽略分析模型改写正文，图片在正文末尾合成。

完整请求字段、状态/事件、合法动作及A3边界见 [A2接口契约](../../docs/agent/a2-contract.md)。

## 实际验证结果

| 验证 | 命令/入口 | 结果与证据 |
|---|---|---|
| 最终后端回归与package，断网运行 | `node tools/dev-harness/a2-final-backend.mjs <live.json>` | **63/63，0失败0跳过，package通过**：[最终日志](runs/2026-09-09T08-34-27-747Z-live/final-backend/regression-package.txt) |
| A0/A1/A2固定回归入口 | `node tools/dev-harness/run.mjs a2` / `a2-mock` | 实际选择A0BaselineTest,A1*Test,A2*Test，不是只运行A0；[全量运行](runs/2026-09-09T08-22-36-997Z/summary.json)中同63例两次通过，后续修复以最终日志为准 |
| 真实Spring HTTP/登录/Redis/MySQL/异步事务 | `node tools/dev-harness/a2-live.mjs start`、`stop <live.json>` | **9/9，0失败0跳过**：[Surefire](runs/2026-09-09T08-34-27-747Z-live/com.yupi.template.intervention.A2HttpIT.txt)、[日志](runs/2026-09-09T08-34-27-747Z-live/http-tests.txt) |
| 独立MySQL迁移 | live入口创建tmpfs库，A1/A2迁移各跑两次 | 通过；保留合成既有行，验证MEDIUMTEXT，JDBC/Mapper一起回滚：[迁移记录](runs/2026-09-09T08-34-27-747Z-live/migration.json) |
| 最终前端类型/build | `node tools/dev-harness/a2-front.mjs <live.json>` | **两项通过**：[结果](runs/2026-09-09T08-37-16-312Z-front/summary.json) |
| 前端Mock SSE兼容 | a2完整入口内执行a1-sse-test.cjs | **4/4**：[日志](runs/2026-09-09T08-22-36-997Z/sse-compatibility.txt) |
| 真实Chrome+实际后端联调 | `node tools/dev-harness/a2-browser.mjs <live.json>` | **32项检查通过；12张截图已逐张检查**：[浏览器结果](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/result.json) |
| 原contextLoads | 最终后端入口单独执行MainApplicationTests | **既有失败1例**：缺DashScope API key，未改断言/删除测试：[日志](runs/2026-09-09T08-34-27-747Z-live/final-backend/existing-context.txt) |
| 全仓lint | a2-front内`npm run lint:check` | **既有16错误1警告**，与A0/A1相同；新增文件无lint错误：[日志](runs/2026-09-09T08-37-16-312Z-front/lint-check.txt) |
| 真实云模型/图片API | 非默认验收依赖 | **NOT_RUN**；本次未消费真实生成API，不将Mock或A0历史冒烟记作A2实测 |

63个纯回归 = A0 5 + A1 Loop37/Integration9/Persistence5 + A2 Round3/Media4。另9个HTTP集成测试，合计**72个独立Java测试**通过，不能把多次运行累加成不同测试。HTTP覆盖：登录/越权、并发接受、单图失败后重试、版本/同编号内容冲突、编辑后追加版本且重复提交不重置、无修改/缺确认拒绝、旧文章/刷新读取、迁移保留数据、两种数据库访问方式共同回滚。

HTTP与浏览器联调从隔离库中的已保存NEEDS_REVIEW草稿开始，云模型、ImageGenerationTool及COS边界固定Mock；Controller、权限、事务、线程池、数据库、Redis会话、HTTP/SSE均真实。正常初稿生成仍由A0/A1回归覆盖，本次不宣称真实云模型全流程质量通过。

## 可复现案例与视觉证据

[case-evolution.json](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/case-evolution.json)保存每次截图对应的真实GET响应，包括版本正文、评审、人工决定、媒体状态。

1. 人工接受：v0问题“需要外部证据” → 勾选事实尚未核查 → HUMAN_ACCEPTED，原问题/正文保留 → image-1失败、image-2为PICSUM降级 → 只重试image-1 → COMPLETED；image-1尝试2次、image-2仍1次且继续标记降级。
2. 编辑重评：原v0保留 → 人工保存v1，其中p2为“REVISE_ME 请解释得更清楚。” → Reviewer报受众问题 → 局部修订v2的p2为“选择一个日常小任务，从第一步开始。” → PASS，本轮1/2；另一段“保留这一段，不要重写。”逐字保留。图文状态独立显示配图待重试。
3. 手机长稿：旧v0无review，v1长文可查看 → 编辑新轮v2 → 自动修订v3；短暂断网900ms后状态继续从GET更新，刷新仍为v3。测试不涉及事件重放或进程重启恢复。

截图（均已打开检查）：[桌面问题](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/01-desktop-review.png)、[接受确认](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/02-human-accept.png)、[失败与降级](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/03-media-failure.png)、[单图重试后](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/04-human-complete.png)、[评审中](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/05-reviewing.png)、[修订中](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/06-revising.png)、[版本差异](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/07-version-diff.png)、[旧文章](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/08-old-article.png)、[手机长稿](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/09-mobile-review.png)、[无review旧版本](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/10-mobile-old-version.png)、[手机编辑](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/11-mobile-editor.png)、[手机刷新恢复](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/12-mobile-restored.png)。

视口1440×1000/390×844；没有横向溢出，移动端按钮纵向排列，弹窗和长稿内部可滚动，未发现遮挡关键动作。实际检查Markdown活动HTML过滤。控制台**0错误/警告**：[console](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/console.json)。网络**0个HTTP≥400，2个ERR_ABORTED且canceled=true**：[network](runs/2026-09-09T08-34-27-747Z-live/browser-2026-09-09T08-45-08-590Z/network.json)。不把主动取消当作生成失败，也不声称浏览器从未中断请求。

## 中间失败与修复

- 新HTTP测试括号错误、新UI使用Array.at及事件变量错误导致编译/类型失败：已修复，最终编译/类型/build通过。旧失败日志保留在08-11、08-22及08-30各运行目录。
- 首轮真实HTTP为8例中4失败1错误：JDBC直连绕过Flex事务包装，且测试Mock在每例后清空。新增ArticleJdbcConfig并正确保持固定响应，补充双访问方式回滚测试后9/9通过；未放宽原业务断言。[首次HTTP失败](runs/2026-09-09T08-24-31-165Z-live/com.yupi.template.intervention.A2HttpIT.txt)。
- 隔离internal网络不暴露端口、复用尚未安装完毕的依赖、只读挂载缺目录等Harness问题：限定只复用完成npm-ci的副本，测试前端接入bridge并仅绑定本机。失败产物未覆盖。
- CUA/本地view_image因Windows沙箱1385不能启动，使用已安装Chrome独立profile/CDP及直接读取原始PNG完成实际浏览器和视觉检查，无插件/模型安装。
- 浏览器首轮测试端口因测试容器重启变化而拒绝连接；第二轮Vite冷加载30秒超时，资源200、无控制台错误。恢复固定端口并将仅页面冷加载预算设为120秒后32项通过。业务等待与断言未降低。

## 启动、回退与环境状态

完整可重复命令见 [dev-harness README](../../tools/dev-harness/README.md)。先运行a2安装隔离依赖，再live start；等待输出的a2-http-ready.json且failedTests为空；运行browser；stop收集结果并清理本次容器。工具需要Docker和Node24（本机Codex运行时可用）、已安装Chrome；不读取应用本地密钥或用户数据库配置。

实际启用需在另行安排的环境依次执行A1/A2增量SQL，并配置：

```properties
article.agent.orchestrator.enabled=true
article.agent.review-loop.enabled=true
article.agent.intervention.enabled=true
```

关闭intervention即可回到原A1体验；需要时再关闭review-loop。保留新增表/历史，不反向缩列或删除数据。已暂停任务不会在关闭开关后自动继续。未默认启动或重启既有服务。

**结束时环境观察**：原4个ai-passage容器在08:45 UTC（北京时间16:45）收到停止信号并退出，OOM=false；用户已于2026-09-10确认这是主动停止，不作为故障修复。本次发出的清理命令仅指定a2-live前缀容器/网络，没有重启原服务。[退出事件](container-events.txt)、[结束状态](final-user-container-state.txt)。开始时原服务运行，结束时已退出，不能描述为全程健康。所有A2测试容器已清理；源码、日志、截图保留。

## A3依赖与待办

A2持久操作记录和媒体快照可供A3使用，但尚无进程重启自动恢复、租约/心跳、外部图片API幂等、取消/全流程超时、SSE序号重放或独立图片额度。崩溃后的QUEUED/RUNNING须先解决不确定外部结果，再恢复；不得重置本轮修订预算。下一阶段应围绕这些真实状态和接口实现可靠性，并安排升级/回退演练及真实API冒烟；本次不实现A3/B。
