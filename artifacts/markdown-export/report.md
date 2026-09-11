# Markdown图片导出修复

截图原导出把站内鉴权路径/api/images/...写入MD，离线编辑器没有站点与登录上下文，因此不能加载。新GET /article/{taskId}/export.md复用文章鉴权，只内嵌文章images清单内的本地PNG为data:image/png;base64，不放宽站内Markdown危险协议过滤，不公开图片或添加密钥。16张/40MiB原图数据上限，读取失败不伪报导出成功；正文包含不属于清单的本地引用则拒绝导出。旧COS等外部链接仍为外链，不进行任意URL下载。

单文件MD适用于支持data图片的预览器，源代码模式不会展示图片，部分编辑器/平台会禁止data图。故保留并推荐ZIP：article.md、images/*.png、index.html和中文README；完整解压后普通相对路径可离线显示。index.html安全转义原文并在原段落位置展示图片，不再仅在末尾放图库。

前端下载改用后端最新持久图文，按钮标注单文件MD与图文ZIP；失败捕获和图片兼容提示明确。未调用模型、未操作用户服务和库、无需新SQL、未提交推送。

验证见compile.json/typecheck.json/tests.json；浏览器及用户实际编辑器兼容性未运行，交由用户验证。

使用新版导出：用户自行执行 docker compose up -d --build backend frontend，然后刷新文章详情并重新下载。旧下载文件不会自动改变。本次未操作原容器，无数据库迁移。Markdown源码模式本来只显示语法，需切换预览；为最大兼容性建议图文ZIP，完整解压后打开index.html。

最终结果：后端隔离离线package退出0；前端vue-tsc退出0；25项专项测试全部通过（新增导出3、图片访问3、本地存储10、配图定位与审稿9），失败/错误/跳过均0。源码与JAR SHA256见source-fingerprint.json和build-fingerprint.json。没有执行真实浏览器/编辑器或收费API验收。
