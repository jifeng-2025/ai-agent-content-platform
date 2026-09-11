# A4 本地存图与双Key候选验收报告

结论：**ENGINEERING_PASS；真实API NOT_RUN；分发授权 UNRESOLVED**。工程最小闭环已完成并重新验收，可交付工程候选版；不能标记A4全部通过或正式发布v0.1.0。未commit/push/tag/Release/部署，未迁移用户库或操作原用户容器。B/H1/D1未实现。

## 本次实现

- 默认 `ImageStorage=local`，COS条件启用；local不初始化COS客户端。仅需百炼文字Key和Google Gemini图片Key这两个外部模型凭据，数据库密码由初始化工具随机生成。不是任意图片供应商Key通用。
- `storage/LocalImageStorage.java`：随机对象ID、受限根目录、栅格格式/大小/像素限制、重编码PNG、同目录原子写入；拒绝路径穿越、SVG/HTML、内网下载及自动重定向。
- `controller/ArticleImageController.java`：登录及文章归属校验的同源图片接口和ZIP导出；不返回磁盘路径、不读取任意服务器文件。ZIP包含Markdown、相对路径PNG和安全离线HTML。
- `storage/GeneratedImageStore.java`、`ImageServiceStrategy.java`、`RuntimeLedger.java`：先留存生成字节，存储失败单独记录为STORAGE_FAILED，重试复用字节；不确定则暂停，不再次盲目调用收费模型。
- `NanoBananaConfig`及prod配置修正为 `nano-banana`；`IMAGE_MODEL_API_KEY`统一绑定，模型/端点可配置。普通登录用户默认Gemini不再要求VIP。旧COS图片/文章保留，不搬迁或删除。
- Compose新增持久图片卷与幂等增量SQL；初始化工具生成缺失的同源前端配置，保护已有.env和env.ts。README、CHANGELOG、候选发布说明及接口契约已同步。

完整接口、限制及存储说明见 [契约](../../docs/agent/a4-local-storage-contract.md)，启动/备份见 [README](../../README.md)。

## 固定候选与证据

- 247个业务/测试/构建配置文件，源码SHA256：`c323e006b168a13e224b9885ccd01a23f1dc8c51a4caf496ed10f7ca75c1c848`。
- 最终JAR SHA256：`4492f3371d37038f0ef8b5a7127182dc31ebc55a56809b6780a5e8a24fe70880`。
- [候选清单](candidate.json)、[源码复核](source-verification.json)、[构建指纹](build-fingerprints.json)、[最终门禁](final-gate.json)。各回归快照源码及Java class与打包JAR一致，最终工作区业务源码无变化。
- [验证统计](validation.json)、[实际命令/退出码/耗时](commands.json)、[证据指纹索引](evidence-index.json)、[截图实看记录](visual-review.json)、[五案例对照](five-cases.md)、[完整故障时间线](fault-timeline.md)。脱敏合成数据证据在 `local-storage-evidence/`。

## 实际验证结果

|范围|实际数量/结果|
|---|---|
|Java功能回归|102/102：88纯回归（两次运行不重复计数）+13真实HTTP+1真实MySQL/进程恢复；0失败/跳过|
|新增存储覆盖|14项Java：格式/超限/路径、归属、ZIP、保存失败复用、模型异常分类及实际Spring配置绑定|
|前端|类型检查/build通过；旧SSE4项、Runtime SSE6项通过|
|完整故障矩阵|12项通过；实际SIGKILL/重启、双Worker暂停/接管、迟到响应及供应商逐请求提交次数证据|
|实际Chrome|143项检查；39张最终截图及1张同候选稳定上限补图，40张均已逐张查看|
|本地HTTP/卷/ZIP|14项通过：匿名/越权/路径拒绝、PNG、ZIP、零COS环境、容器替换后字节一致|
|实际Markdown导出|读取浏览器下载的210字节文件，正文和本地图片引用通过；实际文件留在忽略目录|
|配置初始化|4项通过：随机数据库密码、缺失前端配置生成、两种已有文件不覆盖|
|独立Compose|实际JAR新装、旧数据升级、4迁移重复执行、健康/登录、关闭与恢复开关的非破坏回退通过|

新装/升级使用独立容器、网络和命名卷；实际运行最终MainApplication JAR及前端dist，不使用JUnit代替安装应用。环境差异明确：云边界为测试专用HTTP插件、a2/a4隔离profile、相对/api、缓存Maven运行镜像和Nginx基础镜像；未执行面向用户环境的生产镜像部署或生产库升级。默认Runtime/评审/人工开关仍关闭。

恢复测试额外在生成字节已写入独立MySQL、磁盘保存失败后SIGKILL，第二JVM复用同一字节且不再调用模型。外部成功丢响应通过查询恢复，提交次数仍1；不能推广为所有真实供应商跨系统exactly-once。

本地PNG SHA256：`e0406aa69b96445a29e13e070bfa4639694ecdbb371b5fceb3feb90627cabd87`；实际ZIP SHA256：`fc7ce2fd1dc9d17efa7cc2622b64593427d9b484de8b5a27569230c77a25626a`。解压后阻断HTTP(S)，桌面/移动端均加载包内图片。截图图像明确标有A4 MOCK，不能据此宣称真实图文质量提高。

## 失败分类与同版本重跑

1. 既有失败保留：contextLoads缺DashScope配置（1 error）；lint16错误1警告。构建的大chunk提示未通过放宽阈值消除。
2. 中间候选Java字符串转义编译错误已修复；模型空结果分类修正后重新冻结，06:56试跑被主动中断；这些中间结果不计入最终PASS。
3. 最终候选首段 `gate-2026-09-10T07-00-40-896Z` 已通过全部回归和完整故障矩阵，但local-http脚本在同一Session切换用户后错误复用原Cookie，ZIP被服务端正确拒绝。修复的是测试会话隔离，未放宽业务鉴权。
4. 使用相同源码/JAR执行 `a4-run.mjs --install-only`，完整重跑新装、页面、导出、重建、升级和最终证据门禁，记录为 `gate-2026-09-10T08-10-21-072Z`。首轮失败保留，不宣称首个命令退出0；没有拼接旧业务版本PASS。

最终新增未解决功能回归：0。视觉小项：原英文错误码、极快Mock下短暂旧Toast和一张过渡状态截图；保留同候选稳定待人工补图。Runtime主动断网产生预期SSE错误；其余控制台/HTTP无意外错误，导航取消单列，不当作失败抹除。离线HTML按设计呈现转义Markdown正文及图片，并非完整富文本排版。

## 启动、升级、回退与限制

先运行 `node tools/init-local-env.mjs`，本机填写两个模型Key；按README自行决定是否 `docker compose up -d --build`。本次未执行该命令操作原服务。已有库先备份，执行尚未应用的四个增量SQL；不要删除数据卷。图片卷 `ai-passage-image-data` 对应容器 `/data/images`；备份须同时包含MySQL和图片卷。

关闭新开关是已实测的非破坏回退，保留新表和图片卷。旧二进制不保证认识本地URL，不能直接宣称降级兼容。PNG/JPEG/GIF支持；SVG/WebP等拒绝，GIF转首帧PNG。旧COS链接保留但不自动打包为离线图片。生成字节留存尚无自动清理；非Runtime旧路径不承诺跨进程恢复字节。真实COS服务未调用，兼容依据为旧URL回归和条件配置，非云存储实测。

## 发布准备与待确认

[提交文件清单](submission-files.txt)、[逐文件SHA256](submission-manifest.json)、[敏感/产物审计](privacy-audit.json)、[建议提交说明](suggested-commit-message.txt)。不包含.env、用户数据、数据卷、依赖、缓存、JAR/dist或真实下载文件；保留现有A3/A4未提交修改及上游署名。origin为指定个人仓库，未向任何远端推送。

[分发授权](license-review.md)仍UNRESOLVED：上游README的MIT徽章是线索，缺独立LICENSE不直接等同禁止分发，也不擅自添加MIT。需要适用于当前上游源码的许可证正文/权利人许可及范围，正式分发前确认。

真实API **NOT_RUN**：文字Key存在但账户未验证，图片Key缺失；local完全不需要COS。按 [冒烟方案](paid-smoke-plan.md)，拟1份短文、最多9次文字+1次1K图片、无收费重试；估算US$0.15–0.30，建议申请US$1等值预算，非硬性账单封顶。仅在用户明确授权并本地配置后执行，不能用历史A0冒烟或本次Mock替代。
