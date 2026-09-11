# 管理员模型设置交付

状态：COMPILE_PASS_USER_VALIDATION_PENDING。按用户最新要求只做编译，**测试执行数0**；功能、安全集成、迁移/重启、桌面/手机和真实API全部交由用户验证，真实API NOT_RUN。未使用实际Key，没有迁移原用户库或启动原容器，没有提交/推送。

实现：管理员模型设置页面；文字DashScope/兼容聊天（DeepSeek官方与方舟）、图片demo/Gemini/豆包；多配置默认值，Key一次性提交/AES-GCM加密和独立主密钥卷，空Key保留/显式清除；同源/CSRF/管理员检查和安全异常处理；动态新任务选择、持久配置版本；安全HTTPS/DNS/禁重定向传输；免费格式检查及显式收费单次测试、每日限额/请求去重/脱敏usage。

|检查|结果|证据|
|---|---|---|
|后端隔离Maven package（跳过测试）|PASS；144857ms|compile.log / compile.json|
|前端vue-tsc|退出码0|typecheck.log|
|前端Linux Vite build|退出码0；220329ms|frontend-linux-build.log|
|功能/权限/加密重启/SSRF/旧流程回归|交由用户验证|未执行，不沿用旧PASS|
|浏览器桌面/手机与实际收费调用|交由用户验证 / NOT_RUN|无本轮截图|

编译过程中修正Java转义错误；Windows构建遇到已有Linux Rollup缓存平台不匹配，最终改在Linux隔离构建；只读Vite临时目录限制已解除。没有修改依赖锁或删除测试。既有lint16错误1警告、contextLoads旧配置问题本轮未重跑；不能宣称新候选已修复或继承其结果。

候选源码SHA256：b66545cd900b3ea5f04135e22ab8912a068501bfea57f9240f1a7b4fe7bb40f5（257文件，逐文件见source-fingerprint.json）；JAR见build-fingerprint.json。源快照排除私有.env/application-local/env.ts，前端仅生成同源/api配置；编译容器断网，未加载Mock/真实供应商发起调用。

操作与升级：[最短清单](../../docs/agent/model-settings.md)。旧库须用户备份后执行sql/add_model_settings.sql，新增主密钥卷，重建后进入/admin/models。原.env未修改，格式诊断为有效UTF-8/无重复字段；不能据此断定所有中文注释语义正常。

限制：旧未完成任务若无文字配置快照，拒绝猜测旧模型，需要新建任务；旧文章仍保留。停用替代硬删除；轮换Key不会换配置协议/地址。配置保存不等于账户已验证。外部调用不保证exactly-once，实际测试超时禁止盲目重试。必须备份数据库+图片+主密钥。完整A4/公开分发尚未满足，许可证沿用历史UNRESOLVED结论，没有擅自添加许可证。

关键代码：modelconfig/、ImageProfileStore、RuntimeExternal、ModelSettingsPage.vue、add_model_settings.sql。完整候选待提交清单由artifacts/a4/submission-files.txt列出（含此前未提交A3/A4）；提交前须用户实战、安全复核和上游授权确认。建议提交说明：feat: add admin model settings with encrypted credentials and pinned task profiles。

收尾：专用model-settings-build构建卷已移除，构建容器均已自动删除；没有运行用户服务。Git暂存区为空。待提交模式扫描680项未发现命中（不能代替完整安全审核），清单另含3个自身审计文件。
