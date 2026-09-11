# A4 分发授权核实（2026-09-10）

结论：**UNRESOLVED，未证明可对外分发完整衍生源码/JAR/镜像或商业服务**。未添加 MIT/Apache 等替代许可证；继续本地技术验收。

| 原始来源 | 核查结果与适用范围 |
|---|---|
| [上游仓库](https://github.com/yuyuanweb/ai-passage-creator) / [本地上游对应 README](https://github.com/yuyuanweb/ai-passage-creator/blob/9a7dbf1ff58ec68115f2c0c7310b680b2febe370/README.md) | README 有 MIT 徽章、编程导航作者链接；本地固定上游完整文件树无 LICENSE/COPYING/NOTICE/terms。徽章是许可证意图线索，缺正文、版权声明及明确覆盖范围，尚不足以完成本次发布授权确认。 |
| [GitHub 授权说明](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository) / [条款 D.5](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service#5-license-grant-to-other-users) | 公开仓库允许平台功能内查看/fork；这不自动等于可任意站外分发、再许可或商业化。 |
| [编程导航](https://www.codefather.cn/) | 上游署名指向此站。未取得具体课程购买协议或针对该仓库版本的源码授权，不能推断会员资格等于分发许可。主域抓取失败，检索结果未提供针对本仓库的完整授权。 |
| [百炼服务协议](https://terms.alicdn.com/legal-agreement/terms/common_platform_service/20230728213935489/20230728213935489.html) / [特别说明](https://help.aliyun.com/zh/model-studio/bailian-service-notes) | 约束模型服务及输入输出使用；不能替代上游程序代码的版权许可。账户适用协议以实际签约版本为准。 |
| [Gemini API 条款](https://ai.google.dev/gemini-api/terms#use-of-generated-content) | Google 不主张生成内容所有权，但用户仍负责合法使用，输出可能相似；不是对上游源码或第三方素材的授权。 |

核查限制：公共 GitHub HTML 已查看，匿名 API 403，未能取得当前远端完整递归树。固定本地上游证据见 upstream-license-check.json。未声称当前所有远端分支均无许可证，也未判断既往 G0 推送违法。

需要用户提供：权利人发布的许可证原文及对应仓库/提交，或权利人书面许可/适用课程协议。应明确允许修改后源码公开发布、二进制/镜像分发、商业使用是否允许，以及署名/再分发条件；仅提供购买凭证或 MIT 徽章不足以说明这些范围。可提供脱敏文本/原始链接，勿提交个人订单或联系人隐私。若协议只允许个人学习，本地学习与公开发布应分别处理。

上游作者链接、源码 author 注释、Git 历史与 upstream 关联均保留。依赖各自许可证与图标/图片权利另行适用；本次未形成完整第三方 SBOM 法律审计。正式分发结论保持未满足。

2026-09-10演示范围复核：再次打开当前上游GitHub页面，根目录列表仍未展示LICENSE，不能据此断言所有分支/子目录没有许可。固定本地版本的MIT徽章线索与当前网页内容不同，仍以可对应版本的许可正文为待补依据。[Picsum官方页面](https://picsum.photos/)将图片标为来自Unsplash；演示图片属于第三方素材，不是AI生成内容或上游代码授权。当前验收优先用程序PNG，不提交用户下载图片；需纯本地演示时设置article.images.demo.remote-enabled=false。

2026-09-11复核：再次打开上游公开仓库根目录与GitHub许可说明，仍未取得对应上游提交的完整分发许可正文。保留原UNRESOLVED结论，不认定仓库公开即可任意分发，也不因缺独立LICENSE直接断言禁止。无需为本地验证新增许可证。
