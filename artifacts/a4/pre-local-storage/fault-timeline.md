# A4 最终版本真实进程故障

状态：PASS；12 项检查。以下 UTC 时间来自实际测试日志，不是推演。供应商提交/查询次数见 evidence/fault/provider-state.json。

| UTC | 事件 |
|---|---|
| 2026-09-10T04:49:22.391Z | initial JVM ready  |
| 2026-09-10T04:49:22.431Z | isolated frontend reachable  |
| 2026-09-10T04:49:26.743Z | authentication HTTP readiness  |
| 2026-09-10T04:49:27.119Z | POST /user/login  |
| 2026-09-10T04:49:27.514Z | POST /article/create  |
| 2026-09-10T04:49:29.875Z | SIGKILL backend |
| 2026-09-10T04:53:02.936Z | backend JVM ready after restart  |
| 2026-09-10T04:53:05.096Z | queued creation recovered  |
| 2026-09-10T04:53:06.602Z | SIGKILL backend |
| 2026-09-10T04:57:20.096Z | backend JVM ready after restart  |
| 2026-09-10T04:57:21.978Z | POST /article/confirm-title  |
| 2026-09-10T04:57:33.038Z | outline checkpoint saved  |
| 2026-09-10T04:57:33.447Z | POST /article/confirm-outline  |
| 2026-09-10T04:57:34.166Z | external body succeeded while local save pending  |
| 2026-09-10T04:57:36.282Z | SIGKILL backend |
| 2026-09-10T05:02:41.456Z | backend JVM ready after restart  |
| 2026-09-10T05:02:43.958Z | external job queried and full task completed  |
| 2026-09-10T05:05:28.220Z | worker2 JVM ready after restart  |
| 2026-09-10T05:05:28.679Z | POST /article/create  |
| 2026-09-10T05:05:37.618Z | one worker has in-flight external job  |
| 2026-09-10T05:05:38.280Z | pause old worker worker2 |
| 2026-09-10T05:05:54.214Z | other worker reclaimed expired lease  |
| 2026-09-10T05:05:54.555Z | new fence saved queried result  |
| 2026-09-10T05:05:59.033Z | POST /article/create  |
| 2026-09-10T05:05:59.424Z | cancel has in-flight call  |
| 2026-09-10T05:05:59.424Z | GET /article/79b5b497d90c4f6dbba8030ec7d2a41c/runtime  |
| 2026-09-10T05:05:59.792Z | POST /article/79b5b497d90c4f6dbba8030ec7d2a41c/runtime  |
