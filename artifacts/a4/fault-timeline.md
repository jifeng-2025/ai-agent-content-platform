# 当前候选故障时间线

源码 f2134aa4417fc62294609738c377a08e107a2bf1e9dae90f0177f4de2076c7f8；真实MySQL/Redis/HTTP，云边界Mock。

- 2026-09-11T04:06:47.348Z initial JVM ready
- 2026-09-11T04:06:47.640Z isolated frontend reachable
- 2026-09-11T04:07:46.388Z authentication HTTP readiness
- 2026-09-11T04:07:46.775Z POST /user/register
- 2026-09-11T04:07:49.201Z POST /user/login
- 2026-09-11T04:07:49.655Z POST /article/create
- 2026-09-11T04:07:52.163Z SIGKILL
- 2026-09-11T04:12:06.140Z backend JVM ready after restart
- 2026-09-11T04:12:08.281Z queued creation recovered
- 2026-09-11T04:12:09.841Z SIGKILL
- 2026-09-11T04:16:33.550Z backend JVM ready after restart
- 2026-09-11T04:16:35.489Z POST /article/confirm-title
- 2026-09-11T04:16:44.793Z outline checkpoint saved
- 2026-09-11T04:16:45.205Z POST /article/confirm-outline
- 2026-09-11T04:16:46.797Z external body succeeded while local save pending
- 2026-09-11T04:16:48.638Z SIGKILL
- 2026-09-11T04:20:54.257Z backend JVM ready after restart
- 2026-09-11T04:20:56.909Z external job queried and full task completed
- 2026-09-11T04:23:41.783Z worker2 JVM ready after restart
- 2026-09-11T04:23:42.588Z POST /article/create
- 2026-09-11T04:23:51.130Z one worker has in-flight external job
- 2026-09-11T04:23:51.823Z pause old worker
- 2026-09-11T04:24:07.801Z other worker reclaimed expired lease
- 2026-09-11T04:24:08.141Z new fence saved queried result
- 2026-09-11T04:24:12.729Z POST /article/create
- 2026-09-11T04:24:13.149Z cancel has in-flight call
- 2026-09-11T04:24:13.149Z GET /article/5c393caf09d842288ebfc68f846b9328/runtime
- 2026-09-11T04:24:13.623Z POST /article/5c393caf09d842288ebfc68f846b9328/runtime
