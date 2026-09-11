最新默认流程已按用户调整为「主题→确认大纲→图文→建议评审」，新候选仅做编译检查；见[本轮报告](../quick-creation/report.md)。以下历史A4结果不代表新流程已实测。

本轮后续状态：管理员模型设置改变了候选源码。以下是历史专项结果；不能代表新候选已通过。最新见[模型设置报告](../a4-model-settings/report.md)，用户要求功能和真实调用自行验收。

# A4 双供应商候选：用户接手剩余验收

2026-09-11。用户调整分工：停止总门禁，不再启动新安装、浏览器或升级阶段；完成当前进程故障测试后收尾。真实收费API未调用。

| 项目 | 当前结果 |
|---|---|
| 编译/打包、25项供应商测试、A0–A4固定回归 | 124项两次PASS，0失败/错误/跳过 |
| 前端类型/构建、旧/新SSE | PASS；lint保留16错1警告 |
| 真实MySQL进程强制中断/恢复 | 1项PASS |
| 独立MySQL增量 | 测试库重复执行与历史合成文章保留通过；实际Compose升级交由用户验证 |
| 真实HTTP接口 | 14项PASS（重跑仍通过） |
| Runtime桌面/手机浏览器 | 30项PASS，8截图逐张实看；其他页面全链路交由用户验证 |
| 当前进程故障矩阵 | 12项PASS，真实进程重启/双Worker/取消/SSE；外部提交次数证据已归档 |
| 新安装/升级/完整页面/新选择器/新候选ZIP及重建持久性 | 交由用户验证；旧候选证据不替代 |
| 真实文字、Gemini、豆包 | NOT_RUN；豆包由用户手动体验 |
| 公开分发授权 | UNRESOLVED；未自行添加许可证 |

源码261文件，指纹f2134aa4417fc62294609738c377a08e107a2bf1e9dae90f0177f4de2076c7f8。JAR SHA256：4d86e34cb5ddbe15be4c0b2e6154e9816c7b54e2e98bcfdfb6f82794ed0753df。

## 修改与限制

Gemini原生generateContent与方舟images/generations独立适配；默认DEMO，不要求同时配置两家。任务创建固定provider/model/规格，复用Runtime账本、取消/fence/预算、生成留存、本地鉴权图片与ZIP；不跨供应商收费切换、不确定结果暂停。错误分类/安全请求ID/usage元数据已贯通；usage不是账单，实际费用未知。同步供应商无查询/幂等/取消承诺，测试供应商能力不能外推。

关键入口：service/image/ImageProviderProtocol、ImageProfileStore、ImageHttpTransport；DoubaoImageService、NanoBananaService；sql/add_image_providers.sql；ArticleCreatePage、ArticleReviewPanel。新增配置绑定/协议/错误/本地HTTP/缓存与Runtime测试，前端配置选项仅表示已配置，不表示账户已验证。

## 失败分类

既有contextLoads缺DashScope配置（1错误）、lint16错误1警告原样保留。新增测试最初JdbcTemplate重载歧义已修复。浏览器首轮登录等待失败：空白截图、无console异常、模块HTTP200；导航条件修正为目标URL+readyState，原超时/断言未放宽，HTTP与浏览器重跑通过。失败证据仍在artifacts/a3/runs/2026-09-11T03-33-41-352Z-live。

总调度停止时Windows连带终止等待就绪的故障驱动；复用同一已编译环境继续当前故障测试，不启动后续阶段。

## 用户下一步

按[最短实战清单](user-trial-checklist.md)在本机配置豆包、备份、执行迁移、重建并体验。旧四容器仅做只读检查，仍停止；原库表结构未连接检查，未改用户数据。完整A4不标记PASS，未提交/推送/部署/B/H1。待提交文件见submission-files.txt，建议提交说明见实战清单。

## 最终结论与证据

**SCOPED_ENGINEERING_PASS：已执行范围通过，完整A4未全部验收。** 139项独立Java功能测试（124纯回归两次、14真实HTTP、1真实MySQL进程恢复），12项进程故障，30项Runtime浏览器检查，8截图实际查看。源码/编译类/JAR一致性检查无差异。新安装、页面全链路/新选择器、升级、完整本地图重建/ZIP和真实API均交由用户验证；不使用旧版本PASS替代。

证据：[validation.json](validation.json)、[源码一致性](source-verification.json)、[构建指纹](build-fingerprints.json)、[命令/退出码/耗时](commands.json)、[故障时间线](fault-timeline.md)、[已归档43份证据](evidence-index.json)、[8截图实际检查](visual-review.json)。控制台仅有主动断网的SSE错误及导航取消请求，无新增未解释异常。

本次测试容器/网络全部清理，两Worker正常退出0；原四容器仍Exited，未操作用户服务/数据库。无新业务失败；保留contextLoads与lint既有失败。本轮未暂存、提交、推送、tag、Release或部署。
