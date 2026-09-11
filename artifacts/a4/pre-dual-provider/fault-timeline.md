# 最终演示候选故障时间线

源码 d930f5ff1ceec42313f1f5a76906360d3956e9dc61d3cad0d9ed988f898c196f；真实隔离MySQL/Redis/HTTP及实际进程中断/重启。云边界Mock。

- 2026-09-10T10:33:20.666Z initial JVM ready
- 2026-09-10T10:33:20.823Z isolated frontend reachable
- 2026-09-10T10:34:06.960Z authentication HTTP readiness
- 2026-09-10T10:34:07.355Z POST /user/register
- 2026-09-10T10:34:08.962Z POST /user/login
- 2026-09-10T10:34:09.183Z POST /article/create
- 2026-09-10T10:34:11.593Z SIGKILL (backend)
- 2026-09-10T10:37:47.075Z backend JVM ready after restart
- 2026-09-10T10:37:49.041Z queued creation recovered
- 2026-09-10T10:37:50.525Z SIGKILL (backend)
- 2026-09-10T10:41:02.352Z backend JVM ready after restart
- 2026-09-10T10:41:04.273Z POST /article/confirm-title
- 2026-09-10T10:41:14.184Z outline checkpoint saved
- 2026-09-10T10:41:14.617Z POST /article/confirm-outline
- 2026-09-10T10:41:15.393Z external body succeeded while local save pending
- 2026-09-10T10:41:17.136Z SIGKILL (backend)
- 2026-09-10T10:46:39.070Z backend JVM ready after restart
- 2026-09-10T10:46:42.453Z external job queried and full task completed
- 2026-09-10T10:49:22.697Z worker2 JVM ready after restart
- 2026-09-10T10:49:23.276Z POST /article/create
- 2026-09-10T10:49:32.418Z one worker has in-flight external job
- 2026-09-10T10:49:33.008Z pause old worker
- 2026-09-10T10:49:48.892Z other worker reclaimed expired lease
- 2026-09-10T10:49:50.066Z new fence saved queried result
- 2026-09-10T10:49:54.332Z POST /article/create
- 2026-09-10T10:49:55.491Z cancel has in-flight call
- 2026-09-10T10:49:55.491Z GET /article/e0162aca0b75426b820d4fb3c14a686c/runtime
- 2026-09-10T10:49:56.008Z POST /article/e0162aca0b75426b820d4fb3c14a686c/runtime

演示PNG容器重建：2026-09-10T11:02:00.069Z；前后字节一致，见demo-evidence/localStorage/result.json。
