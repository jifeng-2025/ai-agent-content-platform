# A4 无图片 Key 演示候选验收

工程门禁 **ENGINEERING_PASS**（真实MySQL/Redis/HTTP/浏览器，文字云边界Mock，演示PNG为生产实现）。真实百炼文字 **NOT_RUN**，尚未收费授权；真实AI生图 **DEFERRED_BY_USER / NOT_RUN**。上游分发许可 **UNRESOLVED**。因此可交付工程演示候选，不能称原完整A4或正式v0.1.0全部通过。未commit/push/tag/Release/部署，原用户容器和数据不动。

## 范围与实现

用户明确选择一个百炼文字Key；默认DEMO无需图片Key/COS，Gemini为可选增强。前端默认DEMO并禁用未配置Gemini，后端同样拒绝（含管理员）。不是先调用Gemini失败再降级。DemoImageService在模型前分流：限定Picsum域名、固定尺寸路径、一次可信重定向、3秒连接/读取和5MB上限，失败用程序绘制PNG。来源PICSUM/DEMO_PNG，始终DEGRADED，标记演示/占位、非AI、无语义匹配保证。固定图片计划不调用文字模型。

沿用本地受限根目录、随机对象ID、原子写入、栅格校验、同源归属鉴权和ZIP相对路径；COS及旧文章不迁移。演示重试仍受持久预算/取消/fence约束，模型费用0；真实收费供应商的不确定结果暂停规则未放松。保存失败与模型失败分开，生成留存测试继续覆盖。运行镜像增加字体和UID1000图片目录权限。

入口：src/main/java/com/yupi/template/service/DemoImageService.java、ImageServiceStrategy.java、ArticleMediaProcessor.java；runtime/RuntimeExternal.java、RuntimeLedger.java；frontend/src/pages/article/ArticleCreatePage.vue。契约：[a4-local-storage-contract](../../docs/agent/a4-local-storage-contract.md)。

## 同版本证据

候选文件 251；源码SHA256 `d930f5ff1ceec42313f1f5a76906360d3956e9dc61d3cad0d9ed988f898c196f`。JAR SHA256 `b7ae1922d1439a4dcb22e29234d92dff0f9fe7e95c8ae0a07bd9a3fd388f37c1`。运行镜像 `sha256:165cd04cf5335f75f7cd28b41f534c01a371120484cd6e426ae6e5bdab566bf6`。详情：[源核查](source-verification.json)、[构建指纹](build-fingerprints.json)、[配置指纹](configuration-fingerprints.json)。旧247文件PASS保留pre-demo，旧236文件保留pre-local-storage，不混入本轮结果。

|验证|实际结果|
|---|---|
|Java功能测试|113/113：99纯回归各运行两次、13真实HTTP、1持久Store进程测试；重复执行不重复算独立用例|
|A0五案例|原fixture5/5；[同主题页面对照](five-cases.md)，不证明真实质量提升|
|进程故障|12项通过，真实SIGKILL/重启、双Worker、供应商成功后查询恢复且提交一次、迟到写入/取消终态；[时间线](fault-timeline.md)|
|前端|类型检查/build通过；旧SSE4+Runtime6通过|
|浏览器|177项检查，44张截图实际查看；[视觉检查](visual-review.json)|
|本地存储/ZIP|18项真实HTTP检查，归属/路径保护、PNG重建字节一致、预算不清零、离线解压图片可读|
|安装升级|独立MySQL/Redis/图片卷，幂等迁移、合成历史数据保留、开关关闭回退与Redis会话保留|
|初始化|5项通过，已有配置不覆盖|

命令入口：`node tools/dev-harness/a4-run.mjs`；本轮刷新等待脚本修正后以`--install-only`重跑安装及全部页面阶段，业务源码/JAR不变；最终artifact gate重新汇总核对。命令退出码/耗时见[commands.json](commands.json)，原中断分类见[demo-intermediate-runs.json](demo-intermediate-runs.json)。无删测试、放宽业务断言或扩大超时。详细结果见[validation](validation.json)、[证据索引](evidence-index.json)。

## 演示闭环实测

从正常页面创建、标题选择/刷新、大纲确认进入正文；一次局部修订通过，保留无关段落，DEMO_PNG本地保存合成。刷新图片可读，单图重试不变正文；缺证据案例人工确认后继续演示配图。账本记录3次image:DEMO（含重试和人工继续），预留/实际模型费用均0；没有image:NANO_BANANA，测试供应商imageAttempts=0。见[DEMO调用证据](demo-evidence/defaultLocal/demo-provider-evidence.json)。内部网络无法访问Picsum，实际生产PNG兜底通过；宿主外网Picsum可达性另见[picsum-reachability](picsum-reachability.json)，不是AI生图验证。

## 失败分类与限制

既有contextLoads缺DashScope配置1error、lint16error/1warning保留；不宣称全仓全绿。一次新增浏览器脚本失败是刷新后图片元素未挂载即读取naturalWidth，改为等待元素且naturalWidth>0，原30秒期限不变；已重跑。视觉已知：旧侧栏步骤/进度可能滞后于中间页；部分停止原因显示英文枚举，均明确非成功。详见视觉记录。

实际应用安装使用Dockerfile生产JRE运行阶段加同一已测试JAR，非JUnit应用；未重新执行Dockerfile Maven在线下载构建阶段。云边界插件只模拟文本/旧图片测试，明确排除image:DEMO。尚无真实文字内容质量与账单验证；Gemini真实生图延后；非Runtime旧路径不承诺跨进程生成字节恢复；孤立图片暂不自动回收；多实例共享存储/自动部署不在本次。

## 启动、回退与提交准备

见[README](../../README.md)：`node tools/init-local-env.mjs`生成随机数据库密码及同源前端配置，保留已有文件；本地填百炼Key，图片Key可选。新Review/Intervention/Runtime开关仍默认关闭。图片命名卷挂载/data/images，备份须同步MySQL和图片卷；关闭开关作非破坏回退，保留表/卷；不保证旧二进制识别新本地图，不运行用户down -v。

origin为jifeng-2025/ai-agent-content-platform，upstream只保留关联。最终待提交清单见[submission-files.txt](submission-files.txt)，逐文件哈希见[submission-manifest](submission-manifest.json)，[隐私预审](privacy-audit.json)与[差异审查](release-diff-review.json)。排除.env、图片/数据库卷、依赖缓存、JAR/dist、实际ZIP与原始日志，保留上游署名。建议提交说明见[suggested-commit-message](suggested-commit-message.txt)。本轮未暂存或提交。

公开推送仍需对应上游版本的许可证正文/权利人允许公开修改后源码的依据，并待用户明确授权提交推送。无独立LICENSE不直接等于禁止分发，也不自行补MIT。真实文字如继续，仅需确认账户地区及人民币1元、单样本最多8次qwen-plus文字调用；不在聊天发Key，不申请Gemini费用。B/H1/D1未实现。
