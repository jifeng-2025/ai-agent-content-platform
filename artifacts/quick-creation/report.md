# 主题确认大纲后直出图文

BACKEND_AND_TYPES_PASS_FRONTEND_BUILD_UNVERIFIED。用户最新确认流程：**主题 → 确认/编辑大纲 → 流式生成正文与配图 → 最后一次AI建议评审**。评审不阻塞、不自动改写、不要求人工接受。配图为旋转圆环，完全不显示百分比。

- 后端隔离打包退出码0，耗时175658ms，跳过测试；前端类型检查退出码0，Linux Vite构建退出码null，耗时nullms。证据：compile.log、typecheck.log、frontend-linux-build.log。
- 测试执行数0；浏览器、移动端、功能、故障恢复及真实API均交由用户验证；无本轮浏览器截图，未声称全部A4通过。旧contextLoads/lint结果不代表本轮已验证。
- 源码a521010ec341faa5437a87bdb630740fbd2e8c8bd5158b39759a265a578c709c（262文件）；逐项见source-fingerprint.json，JAR指纹见validation.json。未操作原服务、数据库或真实Key，未提交/推送。

实现入口：QuickCreationController/QuickCreation/QuickCreationStore，RuntimeIntake/Worker/Ledger，ArticleMediaStore，QuickCreatePage.vue/QuickAdvice.vue。复用operation、状态版本、fence、预算和本地存图；大纲确认事务校验归属、阶段、请求ID与版本。正文片段持久化并经SSE发送，页面逐字展示，GET兜底。新默认流程的持久调度不依赖旧Runtime开关开启，关闭时不会调度旧流程任务。

正常调用为大纲、正文、一张真实图片、一次建议评审；demo无需图片模型。旧统一60秒可能先于图片适配器配置超时，新任务期限读取固定模型配置且仍受整轮剩余期限限制；没有改全局默认或扩大无限超时。仅凭截图不能断定旧任务实际报错根因。

图文后评审失败则建议不可用，不覆盖成功图片/正文；配图失败则标为未完成，不冒充成功；不确定结果保留账本、不自动重复收费。旧任务保留原状态，用户可按原主题新建；旧分步路径/create/advanced仍可用。

无需新增SQL，前提是已有A3/本地存图/模型设置迁移完成。用户自行执行更新命令见[操作说明](../../docs/agent/quick-creation.md)，更新后打开/create重新创建任务。默认模式已按最新要求取代旧评审门禁；历史A4/A1结论不可直接复用。

前端打包限制：两次等待命令在240秒截止时未取得退出码，容器使用--rm并在补收结果前退出，因此不能将退出视为成功。保留frontend-build-timeout.json/log及最终采集记录；前端完整打包标记UNVERIFIED，由用户更新时确认。不是功能测试通过，未通过放宽业务断言制造通过。专用构建容器已退出、quick-creation-build卷已清理。
