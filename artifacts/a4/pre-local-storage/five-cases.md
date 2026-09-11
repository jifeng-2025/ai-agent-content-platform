# 五案例对照

相同A0 fixture在候选回归中保持5/5通过；额外UI场景使用同主题和固定故障响应，补充要求统一为新手，不作为生成质量对照实验。

|案例|主题|A0固定输出|当前同fixture|额外真实页面场景|
|---|---|---|---|---|
|C01|普通人如何用 AI 提升工作效率|success|PASS|PASS / v1 / COMPLETED|
|C02|周末散步中的小发现|success|PASS|PASS / v0 / COMPLETED|
|C03|给新手解释数据备份|success|PASS|HUMAN_ACCEPTED / v2 / COMPLETED|
|C04|整理书桌的三个步骤|malformed-json|PASS|PASS / v1 / COMPLETED|
|C05|第一次制作旅行清单|model-error|PASS|PASS / v0 / COMPLETED|

完整初稿、修订稿和taskId见 [five-cases.json](five-cases.json)。Mock不证明真实质量提升。
