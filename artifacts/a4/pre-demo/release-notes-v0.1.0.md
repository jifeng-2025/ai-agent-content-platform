# v0.1.0 本地存图候选（未发布）

继承上游Java/Vue图文创作，新增评审/有限局部修订/人工操作、持久Runtime，以及默认本机存图和鉴权ZIP导出。默认只要求百炼文本和Google Gemini图片两个外部Key；COS为可选兼容，旧图不搬迁。

本地存储改造后重新冻结并完成工程验收：102项Java、12项真实进程故障、143项浏览器检查、本地卷重建和ZIP离线、独立新装/升级通过。最终指纹/结果见report.md、validation.json。当前不引用旧候选PASS。真实API未授权，NOT_RUN；上游分发授权范围尚待确认，未擅自添加许可证。保留上游署名与历史。

升级需add_local_image_storage.sql（先有A1/A2/A3迁移），备份MySQL和图片卷；不删除用户卷。评审/人工/Runtime开关仍默认关闭。旧contextLoads/lint失败单独保留。B/H1/D1未实现。

本次不commit/push/tag/Release/部署，只准备提交清单，等待用户确认。
