# A2 评审面板与人工介入契约

A2 扩展 [A1 契约](a1-contract.md)，不替代 A1 历史记录。默认关闭：`article.agent.intervention.enabled=false`。完整新路径须同时开启 orchestrator、review-loop、intervention 三个开关。旧接口、旧请求及无评审文章继续兼容；A2 关闭时 GET 不访问新增两张表。

## 持久化与接口

迁移顺序：既有数据库结构 → `sql/add_article_review.sql` → `sql/add_article_intervention.sql`。后者新增 `article_intervention`（媒体快照、revision）及 `article_operation`（请求、摘要、派发状态），将 article.content/fullContent 扩为 MEDIUMTEXT。仅增量，不删除旧记录。本文不授权迁移用户库。

所有接口沿用 `/api`、会话登录及 `BaseResponse`。业务成功 code=0；未登录40100、无权限40101、参数40000、不存在40400、状态/版本冲突40900；HTTP 200 不等于业务成功。

- `GET /api/article/{taskId}/interventions`：返回 enabled、taskId、articleStatus、phase、revision、reviewTrace、media、allowedActions、errorMessage。
- `POST /api/article/{taskId}/interventions`：提交操作；返回 requestId、status（操作记录状态）、replayed。提交成功仅表示已接收，请继续 GET。
- 现有 `GET /api/article/{taskId}` 返回当前正文/合成稿；现有 `/review` 返回 ReviewTrace。正文详情与控制面板各自从持久接口恢复。

POST 公共字段：requestId（16–64位字母数字、下划线、连字符；推荐UUID）、action、expectedVersion、expectedRevision。后两项均取最近 GET，打开编辑/确认窗口时固定。不要在冲突后自动换版本重新提交。

| action | 合法状态 | 额外字段 | 效果 |
|---|---|---|---|
| ACCEPT | article与trace均NEEDS_REVIEW | acknowledgeRisks=true，note可选≤500字符 | 追加人工决定；原评审不变；继续配图，不调用正文生成 |
| EDIT_REVIEW | article与trace均NEEDS_REVIEW | content非空≤64000字符，必须有实质变化 | 追加全局版本，开启一轮评审，每轮最多2次自动修订 |
| RETRY_IMAGE | IMAGES_FAILED/COMPLETED且有目标媒体记录 | imageId | 只调用该图片工具一次，其他图片和正文不变 |
| CONTINUE_IMAGES | FAILED且trace为PASS/HUMAN_ACCEPTED | 无 | 继续已保存媒体计划；已有可用图不重复请求 |

服务端文章行锁串行校验，锁后读取鉴权与最新状态。requestId+payloadHash持久去重；相同请求返回replayed，不再次派发；同编号不同内容拒绝。expectedRevision覆盖正文版本不变的图片重试。派发由 QUEUED→RUNNING 条件更新唯一认领；前端禁用按钮只是辅助。文章归属沿用原平台管理员可访问规则。

## 状态、版本与事件

ReviewTrace保留原schemaVersion=1并追加可选 round、roundStartVersion、humanDecisions。旧JSON缺字段时分别默认0、0、空数组。currentVersion全局递增：初稿v0；人工编辑追加vN；本轮自动修订计数=currentVersion−roundStartVersion，最大2，程序常量不可由模型改变。历史DraftVersion及各版review保留，review可为空。humanDecisions记录version、userId、decidedAt、note；HUMAN_ACCEPTED不会把原NEEDS_REVIEW评审改成PASS。

- REVIEWING/REVISING：仅文本阶段进行中。
- PASS：文本评审通过，**不代表事实核查或图文完成**。
- HUMAN_ACCEPTED：人工接受风险，**不代表原评审通过**。
- NEEDS_REVIEW：草稿/问题保留，停止配图。
- IMAGES_FAILED：部分配图失败，可重试；全文仍可读取。
- COMPLETED：本次图文合成已完成；仍可能含明确标记的降级/占位图。

media包含不变的template正文与slots数组；每项id、requirement、result（可空）、status、attempts、error。status=PENDING/SUCCESS/FAILED/DEGRADED。失败重试保留旧result，显示“本次失败”；旧result若来自降级也继续标记。配图分析器只用于需求，忽略其改写正文，合成在原正文末尾追加图片与来源提示。

沿用 `/api/article/progress/{taskId}` SSE。新增MEDIA_UPDATED、IMAGES_FAILED；复用REVIEW_UPDATED、NEEDS_REVIEW、ALL_COMPLETE、ERROR。人工操作事件仅type/taskId，客户端据此GET最新状态；不要依赖事件携带完整正文。ALL_COMPLETE只发于图文终态，NEEDS_REVIEW不发送。终态关闭SSE。面板刷新时GET；PROCESSING时SSE+2秒轮询兜底；断流改为轮询，终态或卸载清理连接、计时器及请求。无事件重放。

## A3边界

本次没有进程重启后QUEUED/RUNNING恢复、租约/心跳、外部图片调用幂等、取消或全流程超时预算。崩溃后的未决操作不能自动重试或重置修订预算。A3须先定义认领恢复、外部结果不确定时查询、事件序号与取消语义。A2人工操作不新增文章、不重新扣整篇配额；重复/高频图片调用的独立限额留待A3定义。

回退：关闭intervention（必要时关闭review-loop），保留新增表和数据，不做逆向缩列、不删历史。已暂停/未决任务不会因关闭开关自动恢复。部署与迁移用户环境需单独安排，本次未执行。

A3 Runtime开启时的持久调度、取消、预算与SSE扩展见 [A3契约](a3-contract.md)。本契约的版本/归属/人工决定规则继续有效。
