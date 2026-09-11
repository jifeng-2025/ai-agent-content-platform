# 主题 → 选择标题 → 确认大纲 → 图文 → 建议评审

本轮是用户明确要求的产品流程调整，旧A1/A4「评审先于配图、最多两次修订」不作为新默认流程。旧任务与旧分步入口保留，不自动重新提交已有不确定的收费请求。

- 默认 /create：输入主题并选择一种配图来源 → 生成标题方案并选择一个 → 生成可编辑大纲 → 用户确认 → 打字机式正文 → 1–5张段落配图与合成 → 一次AI评审建议。
- 复用原标题生成器，持久保存方案；用户选择的标题传入大纲、正文及配图，后续阶段不再用主题覆盖标题。正文按照确认的大纲生成，默认约800字。AI评审不改稿、不要求接受、不触发自动修订；不宣称事实核验。
- 配图使用旋转圆环及状态文字，**没有进度百分比或估算百分比**。供应商未提供进度时不捏造。
- 原复杂分步流程保留 /create/advanced；结果页正文优先，旧任务运行/人工处理面板折叠。截图中的旧EXTERNAL_UNCERTAIN任务仍保留，不自动收费重跑，可按原主题新建。

接口：POST /api/article/quick 创建；GET /api/article/quick/{id} 恢复快照；POST /api/article/quick/{id}/outline 确认大纲（请求ID、预期状态版本、归属和合法阶段校验）。SSE沿用 /api/article/progress/{id}，增加QUICK_TEXT/QUICK_STAGE/QUICK_OUTLINE/ADVICE_READY。正文片段按200ms节流持久保存，刷新读取已有正文；新任务动作QUICK_CREATE复用operation/lease/fence/外部账本。

阶段：Q_TITLE → Q_TITLE_WAIT（选择标题，不计执行期限）→ Q_OUTLINE → Q_OUTLINE_WAIT（用户确认，不计执行期限）→ Q_BODY → Q_IMAGES → Q_ADVICE → DONE。新默认流程即使旧Runtime开关关闭也由持久Worker处理；不会因此启动已关闭的旧流程任务。取消与迟到写入仍校验fence，原模型配置快照/生成结果留存/本地图片和ZIP复用。无需新增SQL（前提是已有A3、本地存图和模型设置迁移已经执行）。

正常约5–9次调用：标题、大纲、正文、真实图片1–5张、建议评审1次；demo不调用图片供应商。评审失败只显示建议不可用；真实配图失败保留正文并标明未完成，不伪报图片成功，不盲目重复付费请求。若整轮取消/超时/预算耗尽，保留已有内容。新流程单步期限遵循固定供应商配置的超时（5–180秒），受整轮剩余期限约束，避免原统一60秒早于已配置的图片超时；这不是供应商可查询/可取消的承诺。

## 用户更新与验收

本轮未操作原服务或数据库。已有模型设置版且迁移齐全时，由用户执行：
~~~powershell
Set-Location D:\2026codex\ai-passage-creator
docker compose up -d --build backend frontend
~~~
不需要重新填Key或删除卷。首次升级仍按model-settings.md保留备份、完成原有增量迁移。

打开 /create → 填主题 → 选择标题 → 检查并确认大纲 → 看正文逐字出现与配图旋转圆环 → 最后查看AI建议、图片、Markdown/ZIP。不要用旧卡住任务代替新流程验收。按用户分工，本轮只编译；功能、实际浏览器、刷新恢复和真实API均交由用户验证，不能沿用旧A4 PASS。真实API NOT_RUN。

建议评审主要检查文字与结构，不代表已经看图核验语义匹配；真实图片质量由用户实战确认。

2026-09-11 标题选择补回：POST /api/article/quick/{id}/title 接受方案index、requestId、expectedStateVersion，校验归属、合法阶段、版本和重复请求。GET /api/article/text-capabilities 仅向登录用户返回默认文字配置名称、协议和模型ID，不返回Key或端点。页面将文字配置与图片选择明确区分。旧的等待大纲任务继续兼容。验证见 artifacts/title-selection；旧quick-creation编译证据仅适用修改前版本。

配图提示词重试：创作页与详情页QuickMedia卡片提供POST /article/quick/{id}/image-retry（imageId、prompt、requestId、expectedStateVersion、acknowledgeCost），仅允许已完成或配图失败任务；固定原供应商与预算，不重跑正文和评审。无修改提示词优先缓存恢复；修改后是主动新生图，可能收费。本地PNG转换超限时受限缩小，仍维持5MiB安全上限。诊断与验证见artifacts/image-retry。

## 文字模型、配图数量与审稿Skill

选标题时可选已启用文字配置及1–5张图片。GET /article/quick/text-models 只返回ID/版本/显示名/模型；标题确认新增textConfigId、textConfigVersion、imageCount，旧请求缺省沿用任务文字配置和1张。仅在标题确认事务中切换后续模型快照；标题生成仍使用初始模型，旧标题调用记录保留。选择停用模型或过时配置版本会被拒绝。无需新SQL，数量持久记录在标题确认operation。

正文写好后，从安全正文段落中等距选锚点；不插入代码块或单独标题，提示词取相应章节和段落内容。锚点随media持久保存，重试不移动位置，fullContent/Markdown/ZIP共同使用渲染结果，原content不变。段落不足时可在同段插入多图；没有安全段落时末尾放图。定位可程序验证，真实画面语义契合仍由模型输出及人工检查决定。原预算/超时/取消生效，不因选择5图扩大预算；缺任意一张不能标图文全完成。

审稿规范：src/main/resources/skills/article-advisory-review/SKILL.md；同时安装在本机Codex技能目录，可用$article-advisory-review。AdvisoryReviewSkill从打包资源加载规范，注入主题/标题/补充要求/大纲/段落ID/配图计划及状态。每篇仍只在末尾给一次建议，不自动修订或阻断。仅文字输入不冒充视觉检查；结构校验沿用ReviewJson。Skill并不保证事实全部正确，真实质量评估未执行。

验证/限制见artifacts/composition-options/report.md。

评审恢复：POST /article/quick/{id}/advice-retry 仅重试缺失的建议，需requestId/expectedStateVersion/acknowledgeCost；不重跑正文和图片、不重置预算，旧不确定请求不自动重发。已知官方主机上的DeepSeek V4评审采用thinking.disabled；仅优化评审请求，正文不变，原超时不扩大。诊断见artifacts/advice-timeout/report.md。
