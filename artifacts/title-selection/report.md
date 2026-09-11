# 标题选择与模型说明修正

默认流程：主题→选择标题→确认大纲→流式正文与配图→最后AI建议。文字使用后台默认文字配置，图片独立选择；没有将DeepSeek当生图供应商。标题确认沿用归属/版本/请求ID/持久调度保护；无需新增SQL。

本轮仅编译，结果见 compile.json、typecheck.json；源码清单见source-fingerprint.json。功能测试0项；浏览器、实际模型调用交由用户验证，真实API NOT_RUN。未重建原服务、未迁移数据库、未提交推送。旧报告保留，不沿用旧PASS。

用户更新：在项目目录执行 `docker compose up -d --build backend frontend`，再强制刷新 `/create`。已有任务不重跑；新任务依次选择标题、确认大纲；设置中默认文字模型若为DeepSeek则文字使用该模型，豆包只用于图片。

编译结果：后端离线package退出码0；前端vue-tsc退出码0。完整前端打包本轮未执行，页面/真实调用交由用户验证。
