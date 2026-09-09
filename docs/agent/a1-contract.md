# A1 评审状态与 A2 接口契约

适用：Java 编排路径的后端评审闭环。A1 不联网检索，不提供事实核查结论，也不实现人工确认、恢复或单图重试接口。

## 启用与数据

默认 `article.agent.review-loop.enabled=false`。仅同时设置 `article.agent.orchestrator.enabled=true`、`article.agent.review-loop.enabled=true` 才进入新路径；关闭新开关继续原 StateGraph，关闭编排开关继续旧 ArticleAgentService。请求 DTO 不增加必填字段。

启用前需要在目标库显式执行 `sql/add_article_review.sql`。本任务仅在独立无网络、内存数据盘的 MySQL 验证迁移，没有迁移现有用户库，也未重启容器。新增 `article_review(taskId, reviewJson, updatedTime)`，不改变既有 article 列或已有行。关闭开关保留历史评审记录，不删表。新评审读取接口依赖该表；部署迁移前不要调用它。

确认标题请求的现有 `userDescription` 已由 ArticleService 保存。确认大纲后，ArticleAsyncService 从文章记录提取 topic、userDescription、style、已选标题及确认的大纲，传到 Writer → Reviewer → 局部修订。受众和补充要求共用 userDescription；没有新增独立 audience 生产字段。旧请求不填该字段仍有效。

## 内容与版本

ReviewResult 严格 JSON：

```json
{"schemaVersion":1,"decision":"REVISE","issues":[{"type":"AUDIENCE","severity":"ERROR","sectionId":"p2","reason":"面向新手却未解释术语","suggestedAction":"用日常语言解释"}]}
```

- decision：PASS / REVISE / NEEDS_REVIEW。PASS 的 issues 必须为空，另两种不能为空。
- type：AUDIENCE、STRUCTURE、LENGTH、UNSUPPORTED_NUMBER、UNSUPPORTED_ATTRIBUTION、EVIDENCE_REQUIRED。程序异常另产生 OUTPUT_ERROR、MODEL_ERROR、MODEL_TIMEOUT、NO_PROGRESS。
- severity：WARNING / ERROR。reason、suggestedAction、sectionId 必须非空；不是内部思维链。
- 未知/缺失/额外字段、未知枚举、重复键、JSON 围栏、尾随 JSON、非法段落 ID、不支持的 schemaVersion 均停止为 NEEDS_REVIEW；不猜测或用正则抽取“修好”模型 JSON。
- sectionId 是当前稿件按空行分段的 p1、p2…；`article` 表示全篇问题。不是大纲 section 编号。局部补丁不能增加段落边界或修改标题，未涉及段落及空行分隔原样保留。无法定位的全篇结构/篇幅问题转人工。
- 初稿 version=0，程序硬限制最多 version=1、2。最多三次评审、两次局部修订逻辑调用；模型无权修改预算。相同问题集合未减少时保守停止，不把文本变化直接当成质量改善。相同文本或仅空白变化不生成新版本。
- Reviewer 与修订单次默认 45000ms；`article.agent.review-loop.timeout-ms` 最高 60000ms。响应限 64000 字符。新路径 Writer 等待上限 60 秒；Writer 生成失败沿用 FAILED，不保证保存生成到一半的 token。
- 规则检查新手术语、部分精确数字/机构归因、确认章节标题、显式中文篇幅范围；其余语义要求由 Reviewer 判断。这些风险规则不是全面事实验证。没有经核验的证据链，模型 PASS 不能覆盖规则风险。

ReviewTrace：`schemaVersion=1, status, currentVersion, maxRevisions=2, stopReason, versions[], review`。versions 每项为 `{version, content, review}`，当前稿尚未评审时 review 可空。顶层 review 为当前有效结论；例如版本 2 的原始结论 REVISE，顶层结论提升为 NEEDS_REVIEW。修订异常的原始问题仍可从版本 review 读取。Gson 省略 null 字段，客户端须允许缺省。

status：REVIEWING / REVISING / PASS / NEEDS_REVIEW。PASS 只代表文本评审通过，之后配图仍可能失败；文章真正完成仍以 article.status=COMPLETED 为准。

stopReason：HUMAN_REQUIRED、REVISION_LIMIT、REPEATED_ISSUES、NO_PROGRESS、REVIEW_FAILURE、REVISION_FAILURE。不要据此宣称事实已核验。

## 状态、接口与 SSE

- `GET /api/article/{taskId}`：原接口，article.status 新增 NEEDS_REVIEW，phase 新增 REVIEWING / REVISING / NEEDS_REVIEW；草稿保存在 content、fullContent。待人工不写 completedTime。
- `GET /api/article/{taskId}/review`：登录后仅作者或管理员可读，沿用 BaseResponse，data 为 ReviewTrace；无评审记录时 data=null。文章不存在/无权仍用现有错误约定。
- `REVIEW_UPDATED`：`{type, taskId, reviewTrace}`，每次快照与正文在事务中保存成功后发送，包括初稿、评审、修订及最终文本结论。
- `NEEDS_REVIEW`：`{type, taskId, status:"NEEDS_REVIEW", reviewTrace}`，保存待人工状态后发送，随后关闭 SSE。此路径不配图、不合成、不发送 ALL_COMPLETE、不进入 saveArticleContent。
- `ALL_COMPLETE`：保持原契约，仅评审 PASS 且原配图/合成成功后发送。
- 持久化失败仍 FAILED + ERROR，不发送虚假成功。网络断开不撤销已保存快照；A1 不重放 SSE、不增加事件序号或恢复调度。A2 可通过两个 GET 读取最终状态，不依赖收到全部事件。

前端仅兼容 NEEDS_REVIEW：终止 SSE、提示草稿已保存、进入既有详情页、显示待人工标签。未建设问题列表、版本对比、接受/修改/重试界面。这些是后续 A2 工作，且应明确接口尚未提供人工恢复操作。

## A2 增量

A2的人工决定、全局版本/本轮预算、单图重试与前端恢复接口见 [a2-contract.md](a2-contract.md)。A1 的 PASS 仍只代表文本评审通过；A2 增加 HUMAN_ACCEPTED 及媒体终态，不把人工接受或图文完成改写为事实核查。A2 为 JDBC 显式复用 MyBatis-Flex 事务数据源，确保评审/文章/操作记录同事务提交；默认开关仍关闭。
