# A4 本地图片存储与导出契约

状态：历史无图片Key演示候选ENGINEERING_PASS；当前双供应商候选专项验证已完成，完整安装/页面/升级由用户验收，真实文字/Gemini/豆包均NOT_RUN；247文件原候选 PASS 归档在 artifacts/a4/pre-demo（更早236文件候选在 pre-local-storage）。Runtime 新路径仍默认关闭。

- ImageStorage.save(ImageData, folder) 返回对外引用；默认 LocalImageStorage，COS仅在 article.storage.type=cos 时初始化，通过 Primary 切换写入。LocalImageStorage 始终可读取历史本地图。没有COS凭据可启动local。
- 内部对象ID为32位随机十六进制，不是磁盘路径。数据库图片引用使用 /api/images/{id}。HTTP(S)旧图仍有效，file/data/javascript/任意内部路径不作为图片引用。
- GET /api/images/{id}：登录且拥有引用它的未删除文章才可读；PNG类型，private/no-store、nosniff、sandbox头。无权限/不存在不给文件。不注册静态目录映射。
- GET /api/article/{taskId}/export.zip：复用文章读取鉴权；只读取 article.images 的合法本地对象ID。包含 article.md、images/{id}.png、转义文本的 index.html 和说明。总图片<=16、总字节<=40MB。不获取旧远程图、不打包任意路径。
- 输入默认5MB/1600万像素；PNG/JPEG/GIF解码为PNG，丢弃尾随主动内容和GIF动画。拒绝SVG/HTML及不支持格式；上传COS的新图也执行栅格校验。URL输入限HTTPS、拒绝内网地址与重定向。现有旧图不迁移。
- 根目录由后端配置，命名卷挂载/data/images。生成对象名、临时文件同目录、fsync后原子move，不接受客户端文件名。拒绝根/对象符号链接和路径穿越。
- Runtime已取得的图片字节在新表 article_generated_image 中按taskId+请求摘要留存，再写图片存储。保存失败时slot为FAILED，错误前缀STORAGE_FAILED，账本状态STORAGE_FAILED/事件IMAGE_STORAGE_FAILED；草稿不变，单张重试复用字节。若尚未可靠留存即中断，仍按EXTERNAL_UNCERTAIN暂停，不能盲目再次调用模型。新付费任务在Runtime关闭时也留存已收到图片；完整跨阶段进程恢复仍需启用Runtime。
- 新迁移 add_local_image_storage.sql 幂等，仅增加表；备份包含该表和图片卷。保留失败结果便于恢复，当前不做自动回收或用户旧文件删除。
- GEMINI_API_KEY绑定nano-banana.api-key，旧IMAGE_MODEL_API_KEY保留为兼容别名；Google原生接口，仅支持适配器协议，非通用图片Key。nano-banana.base-url/model/max-output-tokens由后端配置。默认输出限制2048、1K，实际usage/收费另行冒烟记录。
- 原权限边界不变；自托管默认Gemini对登录用户开放，SVG仍受原VIP限制且本地模式拒绝SVG。默认未选图片方式为DEMO；DEMO必须单选。Gemini未配置时后端创建接口拒绝，即使管理员也不例外。

真实API未授权，NOT_RUN。最终验证以 artifacts/a4/report.md、validation.json 与同版本指纹为准。

## 用户选择的演示MVP

- GET /api/article/image-capabilities：登录后返回defaultMethod=DEMO、geminiConfigured/doubaoConfigured布尔值、配置模型和demoNotice，绝不返回密钥。
- DEMO仅是请求策略；实际结果method为PICSUM或DEMO_PNG，slot始终DEGRADED。固定一张封面，不用文字模型生成配图计划；合成附非AI占位/无语义匹配提示，旧合成路径同样标记。
- DemoImageService仅允许https://picsum.photos/800/600，最多一次重定向至https://fastly.picsum.photos/id/数字/800/600.jpg；拒绝其他域名/端口/身份信息/内网解析。连接与读超时各3秒，最多5MB，沿用栅格校验。通用存储下载器仍拒绝重定向。
- article.images.demo.remote-enabled=false可明确禁用外网图库；任何获取失败使用程序绘制PNG，写入失败单独传播STORAGE_FAILED。无Gemini/COS凭据也可创建本地图。
- Runtime演示步骤仍受取消/fence/调用次数/单图重试/期限限制；费用预留与模型实际费用为0。其可恢复重复执行仅允许本地演示步骤，不扩大真实收费供应商的重试权限。未提交文件可能成为孤立对象，当前无自动垃圾回收。
- 真实文字须独立收费授权；真实AI生图明确延后，不作为本次演示范围中的已通过项。

- 演示候选运行镜像补齐fontconfig/DejaVu字体；/data/images归应用UID1000，新命名卷继承该目录权限。隔离安装使用Dockerfile的实际JRE运行阶段与同一测试JAR；未自动修改任何用户已有卷权限。根.dockerignore排除私有本地配置、.env、data和验证缓存。

2026-09-11扩展：本地存储路径保持复用，新增可选豆包与Gemini图片协议，任务模型快照和图片元数据见[a4-image-providers-contract.md](a4-image-providers-contract.md)。本文件中的历史工程数量不替代新版本验证。

2026-09-11 导出修复：GET /article/{taskId}/export.md通过文章鉴权，将本地PNG内嵌为data图片，适用于支持该格式的Markdown预览器；不修改站内URL过滤。ZIP继续作为兼容性更高的离线图文包，含标准MD/相对图片/HTML查看入口，图片按正文位置展示。外部旧图片仍为外链。验证见artifacts/markdown-export/report.md。

### 离线导出入口统一（2026-09-11）
历史列表使用后端export.zip，不再拼接含/api/images的离线MD。详情页新增鉴权export.html，内嵌本地PNG且进行安全基础Markdown排版，双击可读。ZIP内article.md+images使用通用相对路径，index.html同样排版；单文件MD保留data图片兼容限制。无需SQL迁移。

