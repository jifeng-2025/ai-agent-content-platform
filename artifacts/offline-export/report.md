# 离线导出二次修复

根因：前轮只修详情页，历史列表仍自行拼接MD，漏掉图片内嵌；原HTML仅转义Markdown放入pre，未渲染标题/粗体等。本轮统一所有现有导出入口到鉴权后端，删除旧客户端拼接实现。历史列表默认ZIP（标准article.md+images目录），详情页保留单文件MD并新增内嵌PNG的单文件HTML。

HTML为离线安全排版：标题、段落、粗体/强调、引用、列表、代码块、分隔线和图片，限制阅读宽度与图片尺寸、移动端适配，不执行原始HTML或加载脚本。它是基础Markdown子集，暂不承诺完整GFM表格/嵌套列表。旧外链图片仍需网络；本地图片不公开、不跳过鉴权。

MD本身支持图片，但源代码模式不渲染；ZIP里的article.md使用images相对路径，完整解压且保留目录，VS Code按Ctrl+Shift+V预览。单文件MD使用data图片，部分编辑器不支持，建议ZIP或HTML。现有已下载文件不会自动更新。

无需SQL迁移；未访问用户数据库/Key/收费API，未操作用户服务。用户更新命令：docker compose up -d --build backend frontend，然后刷新并重新下载。

验证结果见compile.json、typecheck.json、tests.json。用户真实文章与实际编辑器验收仍由用户执行。

最终验证：后端package、前端vue-tsc均退出0；29项专项测试通过，失败/错误/跳过0。使用实际Java渲染器输出的合成样例，在Chrome file://打开，HTTP(S)全部阻断，桌面1440x1000及手机390x844截图均实际查看：标题层级、段落/列表、正文宽度和配图缩放正常，无横向溢出；图片加载通过，console/network无错误。见browser-2026-09-11T09-44-47-467Z。本次并非用户文章的端到端导出或用户编辑器验证。源码和构建SHA256见指纹文件。
