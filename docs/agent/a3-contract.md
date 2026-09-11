# A3：内容 Runtime 契约

Runtime 默认关闭：`article.runtime.enabled=false`。启用时同时开启原 `article.agent.review-loop.enabled`、`article.agent.intervention.enabled`；继续 Java/Vue/MySQL/Redis，默认云 API。没有 Research/RAG/MCP、事件溯源框架、服务部署或用户库迁移。验证结论及源码指纹见 [A3 报告](../../artifacts/a3/report.md)。

## 持久模型与迁移

执行身份为文章级 `runId` + 操作 `requestId` + 执行代次 `fence`。复用 `article_operation`，新增 phase/checkpointJson/owner/fence/leaseUntil/heartbeatAt/stateVersion；`article_review` 和 A2 media 保持唯一来源。`article_runtime` 保存跨所有操作的剩余预算、工作流状态版本与事件序号。`article_call` 是外部请求账本；`article_event` 是有限保留的通知日志。

`sql/add_article_runtime.sql` 仅增量加列/建表，不清除历史。必须先完成 A1/A2 迁移。当前独立 MySQL 已验证重复执行与文章/操作/评审历史保留；未操作用户库。回退先关闭 Runtime，保留新增表列及待处理记录，不缩列、不删除草稿、不让旧 Worker 接管 Runtime 在途操作。切换开关前应等待或取消在途 Runtime；不承诺跨开关自动接管。

## 阶段与恢复

| 状态/检查点 | 行为及下一步 |
|---|---|
| QUEUED | 事务中写入，轮询器可恢复“提交后未派发” |
| RUNNING | 文章行锁下持久认领；owner、fence 自增及租约落库 |
| 租约过期 | 新 Worker 认领并增加 fence，文章显示 RECOVERING |
| TITLE → TITLE_WAIT | 保存标题方案后等待用户；恢复复用方案 |
| OUTLINE → OUTLINE_WAIT | 保存大纲后等待用户；恢复复用大纲 |
| BODY → REVIEW | 原稿与下一阶段同事务提交，不续写中断半段 token |
| REVIEW → REVIEW/MEDIA/DONE | 每版评审/修订保存；已保存评审复用；每轮最多两次修订 |
| MEDIA → MEDIA/DONE | 保存每张结果与下一阶段；已保存成功/降级/失败结果不自动重跑 |
| DONE | 只补记操作结束，复用已有文章结果 |
| NEEDS_REVIEW / IMAGES_FAILED | 保留版本、问题和媒体，等待 A2 人工操作 |
| EXTERNAL_UNCERTAIN | 外部结果不能确定，停止新自动调用，等待人工核对 |
| CANCELLED / TIMED_OUT / BUDGET_EXHAUSTED | 保留草稿，不能显示完成；迟到 Worker 不能写入 |
| COMPLETED | 图文任务完成，才发送 ALL_COMPLETE |

正常创建、确认标题、确认大纲由 RuntimeIntake 事务入队；A2 ACCEPT、EDIT_REVIEW、RETRY_IMAGE、CONTINUE_IMAGES 在原决策事务内 enroll。原异步 Worker 在 Runtime 开启时让位。没有依赖进程内 Future 的唯一派发记录。

默认 15 秒租约、2 秒心跳、1 秒扫描、每实例两个 Worker。阶段结果、checkpoint、通知在同一短事务提交；每次写入校验 RUNNING、fence 和有效租约。旧 Worker 的网络调用即使迟到，也不能覆盖新 Worker 或取消终态。外部 I/O 不持有数据库事务/行锁。

预算属于整个 run，人工编辑不会重置；全局正文版本和 roundStartVersion 继续使用 A2 契约。自动修订上限硬编码为两次，模型不能提高。已结束阶段不会因重启重跑；未保存的模型结果按外部账本处理。

## 外部请求与预算

稳定 callId = SHA-256(runId、requestId、步骤、参数内容)；账本存参数摘要、状态、尝试次数、查询次数、可获得的 providerJobId、结果、时间及费用字段。敏感请求正文不写入事件日志。调用前先持久登记 IN_FLIGHT 并扣减预算，再调用供应商；SUCCEEDED 结果直接复用。

- 当前生产同步 DashScope / Gemini / 图片组合接口没有接入可依赖的异步任务查询或跨请求去重能力。重启发现未完成账本时暂停，不盲目再调用。
- RuntimeProvider 只有供应商确有能力时才可提供查询/幂等适配；隔离 HTTP Mock 实测支持通过稳定请求 ID 查询。查询优先；NOT_FOUND 只有在适配器明确保证幂等提交时才允许同标识提交。
- 查询本身也计入调用额度，不能靠持续重连无限查询。查询到 PENDING/UNKNOWN 或无法查询时继续暂停。
- Runtime 开启时 Spring 模型 RetryTemplate 限一次尝试；Gemini 请求显式配置 attempts=1。无应用层自动收费重试；不宣称跨供应商 exactly-once。
- 图片获取与上传作为一个有界步骤登记；若收费生图未拿到确定可用结果或发生降级，保守暂停为不确定。可能存在供应商端已生成但本地未保存的孤立结果，不会自动重新收费生成。

默认配置：每步 60 秒、run 累计有效执行租约时间 900 秒、12 次调用、3 次主动图片重试、估算预算 5,000,000 微单位；每次文本预留 100,000、图片 1,000,000。都是服务端持久限制。人工等待、排队以及停机超过有效租约的时间不计入有效执行预算；这不是绝对墙钟截止时间。剩余时间在心跳、检查点及恢复时持久扣减，不因刷新/新一轮重置。正在执行的步骤超过期限后结果不确定，停止自动调用；整个有效执行预算耗尽为 TIMED_OUT。

`reservedCostMicros` 是配置的保守估算预留，不是供应商账单或实际价格承诺。`actualCostMicros=null` 表示未获得费用数据，绝不是免费；全部调用都提供实际值才给出 run 汇总。实际账单可能超过估算，金额需以供应商为准。当前生产真实收费 API：NOT_RUN。

## 人工控制与权限

`GET /api/article/{taskId}/runtime`：登录/归属校验，返回 enabled、runId、stateVersion、status、phase、lastEventId、remainingMs、调用/重试/费用预算、operations、calls、mayStillCharge。旧文章或开关关闭返回 enabled=false，不推断它已评审或被 Runtime 执行。

`POST /api/article/{taskId}/runtime`：

```json
{"requestId":"客户端唯一标识至少16字符","action":"CANCEL","expectedStateVersion":8,"acknowledgePossibleCharge":false}
```

- CANCEL：允许处理中、恢复中、人工等待、图片失败或不确定状态；增加 fence、停止新调用、保留草稿。供应商不支持取消时仍可能收费，界面明确提示。
- RECHECK_EXTERNAL：只在不确定状态重新查询；不支持查询时再次暂停，不授权另一次收费提交。
- RETRY_UNCERTAIN：必须显式 acknowledgePossibleCharge=true。记录人工决定，原不确定步骤标记 AUTHORIZED_RETRY；保留 run/版本/预算，增加尝试并重新扣减调用、费用及适用的图片重试额度。已保存成功步骤继续复用。可能产生重复费用，UI 必须先显示确认框。

服务端对文章加锁后检查拥有者、状态版本、允许动作和请求内容摘要；相同 requestId + 内容重放返回 replayed=true，不重复派发；同标识不同内容或版本过期返回40900。操作记录复用 article_operation。A2 的 expectedVersion/expectedRevision 与人工接受原评审保留规则不变。

新建文章支持可选 requestId；旧请求仍兼容。标题/大纲确认使用确定的操作编号及请求摘要，避免重复确认派发。补充要求 userDescription 仍进入大纲、正文及 Reviewer。可选“AI 自动改纲”尚未纳入账本，在 Runtime 模式明确拒绝；用户仍可直接编辑大纲，不绕过预算调用模型。

## SSE 与界面恢复

继续 `GET /api/article/progress/{taskId}`，仅 Runtime 文章改用持久事件日志。支持 `Last-Event-ID`（优先）或 `?cursor=N`。事件为每篇文章单调 seq，并含 runId/stateVersion/type；SSE id 等于 seq。

补发和后续实时更新都读取同一已提交数据库日志，每批最多100条，没有“先查历史再订阅内存总线”的漏事件窗口。连接注释立即发送并每15秒心跳；注释不是业务事件，不占持久序号。

默认保留最近1000条。游标早于保留范围或高于当前序号，发 SNAPSHOT_REQUIRED + resetCursor；客户端以 GET 快照恢复，不猜测遗漏步骤。终态补发后发送 RUNTIME_SNAPSHOT(terminal=true) 并关闭。旧无 seq 事件仍兼容。

Vue 在会话存储中保存每篇文章游标、丢弃重复 seq；断线后 GET 兜底并重连，终态及卸载清理 EventSource/定时器/请求。正常创作页 URL 保存 taskId，刷新可恢复标题/大纲确认阶段；进入正文阶段转到现有详情/评审面板。文本评审通过、人工接受、图文完成继续明确区分，不宣称事实核查。

## A4 最终同版本复核

2026-09-10：A4 在固定业务源码/JAR 上重新执行完整进程故障矩阵、Store/HTTP/浏览器与安装升级验证。见 [A4报告](../../artifacts/a4/report.md) 和 [源码/构建核对](../../artifacts/a4/source-verification.json)。本轮未修改 A3 业务契约；真实收费供应商仍 NOT_RUN，不用旧 A3 证据替代最终版本。
