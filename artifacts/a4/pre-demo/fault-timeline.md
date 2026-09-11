# 最终本地存储候选故障时间线

源码 c323e006b168a13e224b9885ccd01a23f1dc8c51a4caf496ed10f7ca75c1c848；真实隔离MySQL/Redis/HTTP与实际进程SIGKILL、重启和暂停。云边界Mock。12项通过；预算/修订持久性另由StoreIT验证。供应商逐请求提交/查询次数见local-storage-evidence/fault/provider-state.json。

- 2026-09-10T07:45:18.853Z initial JVM ready
- 2026-09-10T07:45:19.013Z isolated frontend reachable
- 2026-09-10T07:46:04.213Z authentication HTTP readiness
- 2026-09-10T07:46:04.561Z POST /user/register
- 2026-09-10T07:46:06.523Z POST /user/login
- 2026-09-10T07:46:06.825Z POST /article/create
- 2026-09-10T07:46:08.962Z SIGKILL (backend)
- 2026-09-10T07:50:13.466Z backend JVM ready after restart
- 2026-09-10T07:50:15.575Z queued creation recovered
- 2026-09-10T07:50:17.105Z SIGKILL (backend)
- 2026-09-10T07:54:07.177Z backend JVM ready after restart
- 2026-09-10T07:54:09.196Z POST /article/confirm-title
- 2026-09-10T07:54:19.423Z outline checkpoint saved
- 2026-09-10T07:54:19.862Z POST /article/confirm-outline
- 2026-09-10T07:54:21.562Z external body succeeded while local save pending
- 2026-09-10T07:54:23.592Z SIGKILL (backend)
- 2026-09-10T07:58:02.899Z backend JVM ready after restart
- 2026-09-10T07:58:05.756Z external job queried and full task completed
- 2026-09-10T08:00:38.816Z worker2 JVM ready after restart
- 2026-09-10T08:00:39.221Z POST /article/create
- 2026-09-10T08:00:48.794Z one worker has in-flight external job
- 2026-09-10T08:00:49.410Z pause old worker
- 2026-09-10T08:01:04.738Z other worker reclaimed expired lease
- 2026-09-10T08:01:05.885Z new fence saved queried result
- 2026-09-10T08:01:10.462Z POST /article/create
- 2026-09-10T08:01:10.961Z cancel has in-flight call
- 2026-09-10T08:01:10.961Z GET /article/e2a830027b46487383a932319ce4238b/runtime
- 2026-09-10T08:01:11.571Z POST /article/e2a830027b46487383a932319ce4238b/runtime

本地存储容器替换：2026-09-10T08:13:59.274Z；替换前后PNG SHA256相同，见local-storage-evidence/localStorage/result.json。
