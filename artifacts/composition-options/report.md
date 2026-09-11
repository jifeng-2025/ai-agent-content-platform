# 创作选项与审稿Skill

新增标题确认时文字模型/1–5张图片选择，复用任务快照和operation持久数据；后台只开放已启用文字配置的非敏感选项。所选模型用于大纲、正文和最后建议，生成标题仍使用初始模型。正文段落锚点决定图片位置；每图提示词包含对应章节/正文，重试和导出沿用定位，原文不重写。多图未齐全不报COMPLETED，现有预算不变。

可复用Skill在src/main/resources/skills/article-advisory-review/SKILL.md，并安装到用户Codex技能目录。应用实际加载该规范，不只是文档；注入用户要求、大纲、段落及图片状态，解析仍使用严格ReviewJson。未提供图片像素，不声称视觉核验；不保证每次评审准确。

格式验证：原Python quick_validate缺PyYAML，改用已安装js-yaml校验名称、说明、字段、占位符与两份Skill一致性，见skill-validation.json。真实模型审稿质量评测、浏览器及完整安装门禁NOT_RUN；用户实战验证。本轮不读模型Key、不调用云API、不操作用户服务/数据库。

编译和专项结果见compile.json/typecheck.json/tests.json；旧证据不覆盖。更新仍由用户执行 docker compose up -d --build backend frontend，无新增SQL。

最终结果：后端package退出码0；前端vue-tsc退出码0；47项专项测试全部通过（模型选择2、存储10、缩图1、供应商25、配图定位/Skill9），无跳过。前端完整build/浏览器/真实模型质量验证未执行。
