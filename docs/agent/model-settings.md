# 管理员模型设置（本轮功能验收交由用户）

入口：登录已有管理员账号 → 顶部「模型设置」。普通注册用户不能使用。所有新模型调用在后端，Key 不回显、不写浏览器存储。数据库配置优先；未选数据库默认文字配置时保留 DASHSCOPE_API_KEY 环境后备。无需模型 Key 即可启动设置页面（MySQL/Redis和迁移仍须正常）。

## 最短操作

1. 按下方备份、增量迁移、重建启动；使用 http://localhost 打开网页（自定义端口须将 MODEL_ADMIN_ORIGIN 设为浏览器完整 origin）。
2. 新增文字配置：协议选「DeepSeek / 火山方舟兼容聊天」，Base URL 填 https://ark.cn-beijing.volces.com/api/v3（完整 /chat/completions 也会规范化），模型 ID 填方舟账户实际开通的 DeepSeek 模型/接入点 ID；填新 Key，勾选启用和默认文字。DeepSeek 官方账户改用 https://api.deepseek.com；不同账户 Key 不通用。
3. 新增图片配置：协议选豆包，Base URL 填 https://ark.cn-beijing.volces.com/api/v3（完整 /images/generations 也会规范化），模型 ID 填账户可用的 Seedream 图片模型/接入点 ID，尺寸优先 2K。勾选启用、默认图片。也可以继续使用 demo，不要求 Gemini。
4. 先点格式检查（仅格式，不验证账户）；保存后自行选择是否点击实际测试：文字一次、最多16输出 token；图片一次、最多1张；可能收费，每管理员每天最多5次，不自动重试。Key 留空编辑保留；清除必须显式确认并停用。
5. 创建短文章，确认页面实际图片供应商/模型，完成标题→大纲→正文/评审→必要人工接受→图片→查看/ZIP。demo 必须显示占位；真实配图必须与正文核对。检查手机页面、刷新、图片持久化、ZIP离线图片；本轮均交由用户验证。

## 仅由用户执行：现有库升级

本轮未运行以下命令，未操作原服务。已应用 A3/A4 的环境只新增 add_model_settings.sql。更早版本应先按现有 A4 文档补齐历史增量；不要重放 create_table.sql，不要删除数据卷。

~~~powershell
Set-Location D:\2026codex\ai-passage-creator
node tools/init-local-env.mjs
# 保留原 .env/数据库密码；仅不存在时初始化。网页配置模型不需要修改模型环境变量。
# 仅用户决定启动后执行：
docker compose up -d mysql redis
New-Item -ItemType Directory -Force D:\ai-passage-private-backups
icacls D:\ai-passage-private-backups /inheritance:r /grant:r "$($env:USERNAME):(OI)(CI)F"
# SQL备份先落容器文件，避免Windows管道改变编码；不输出密码。
docker compose exec -T mysql sh -c 'umask 077; mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces "$MYSQL_DATABASE" > /tmp/before-model-settings.sql'
# 上一步必须成功，再继续
docker cp ai-passage-mysql:/tmp/before-model-settings.sql D:\ai-passage-private-backups\before-model-settings.sql
docker cp sql/add_model_settings.sql ai-passage-mysql:/tmp/add_model_settings.sql
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /tmp/add_model_settings.sql'
# 每步检查退出码；迁移失败停止。已有镜像先保留为回退标签：
docker image tag ai-passage-backend:latest ai-passage-backend:before-model-settings
docker image tag ai-passage-frontend:latest ai-passage-frontend:before-model-settings
docker compose up -d --build backend frontend
~~~

没有旧镜像的新安装跳过备份镜像/增量迁移步骤，初始化后直接 docker compose up -d --build；空库会挂载11_model_settings.sql。已有管理员无需重建账户。新账号应由已有管理员授予权限；不预置公开管理员密码。

如需体验 A 的新闭环，在原 .env 保留其余字段，设置 REVIEW_LOOP_ENABLED=true、INTERVENTION_ENABLED=true、RUNTIME_ENABLED=true，再重建启动。默认仍关闭。

## 私有备份 / 安全访问

数据库卷 ai-passage-mysql-data、图片卷 ai-passage-image-data、主密钥卷 ai-passage-model-secrets 必须配套备份；备份存项目外受限目录，禁止Git。旧后端尚无图片/密钥挂载时先检查 docker inspect ai-passage-backend --format '{{json .Mounts}}'，不要凭空认定已有卷。

新后端可在停写期间执行以下备份（含主密钥，严禁上传）：
~~~powershell
docker compose exec -T backend sh -c 'umask 077; tar -C /data -cf /tmp/private-model-assets.tar images model-secrets'
docker cp ai-passage-backend:/tmp/private-model-assets.tar D:\ai-passage-private-backups\private-model-assets.tar
docker compose exec -T backend rm /tmp/private-model-assets.tar
~~~
主密钥首次保存Key时生成，AES-256-GCM加密，文件600、目录700（Windows使用owner ACL）。已有密文但主密钥丢失时拒绝重新生成，必须恢复正确备份。不要只迁移数据库。

Compose端口默认只绑定127.0.0.1，后台管理员接口要求配置的同源Origin/Referer + session CSRF，外部必须HTTPS。MODEL_ADMIN_ORIGIN须包含协议与端口且无尾斜杠。Compose仅信任其私网反向代理；勿单独将后端暴露公网。自行公网部署时必须正确配置HTTPS代理和受限网络，不能仅伪造转发头；本轮不部署。

## 配置版本 / 限制

- 模型设置只支持 DashScope原生、兼容聊天、Gemini原生、豆包原生、demo；不是任意协议。Base URL限制公开HTTPS/443；连接时DNS检查、禁私网/元数据、禁代理和重定向、不重试。
- 创建任务事务固定文字配置；图片复用article_image_profile，追加configId/version。配置版本含provider/model/endpoint，不含Key；Runtime外部账本引用固定文字配置。
- 协议/endpoint固定；切换时新增配置。Key轮换只作用于同一配置，历史任务下次调用用该配置新Key，模型版本/endpoint不变。停用阻止新任务使用，不删除原凭据；暂不提供硬删除，避免破坏在途任务。显式清除Key会使后续未缓存调用失败，已完成图片仍复用。
- 升级前的旧文章仍可查看/导出；没有文字快照的旧未完成任务明确拒绝猜测模型，请新建任务，草稿不删除。旧环境Key快照不会复制明文，但仍依赖原环境凭据；迁移网页后请保留有在途任务的旧凭据。
- 支持原生文字SSE；实际测试只返回状态、请求ID和数字usage，不回显模型文字。图片测试保存本地，但不提供测试图片公开链接；文章流程使用原鉴权图片与ZIP。
- 供应商不支持的跨系统幂等/查询/取消不作保证，超时可能已收费，禁止盲目重复点击。实际账单以供应商为准。
- 原SVG兼容路径仍不适合作为本地安全图片输出，本轮不扩展SVG。Research/RAG/H1未实现。

## 出错与非破坏回退

网页显示脱敏分类/请求ID；实际测试审计保存在 model_audit、model_probe，后者保存数字usage与安全结果；不要查询/导出model_config.secret。
~~~powershell
docker compose logs --tail 100 backend
~~~
不要开启HTTP请求头/body或JDBC参数TRACE，不分享原始.env/日志。格式检查不证明连通。若MASTER_KEY_MISSING恢复主密钥，不能清库。

代码回退：将before-model-settings两个镜像重新标为latest，docker compose up -d --no-build backend frontend。保留增量表和全部卷，不回滚/删除数据。旧应用不会读取网页配置，需保留先前环境模型配置；没有旧Key时只能恢复旧站查看。数据库恢复仅在用户明确决定且已备份当前数据后执行。

本轮编译结果见 artifacts/a4-model-settings/report.md；功能、安全用例、重启持久化、安装升级及真实调用均交由用户验证，未沿用旧候选PASS。

协议依据：
- DeepSeek 官方兼容接口：https://api-docs.deepseek.com/
- 方舟聊天接口：https://www.volcengine.com/docs/82379/1494384
- DashScope 原生文字：https://help.aliyun.com/en/model-studio/qwen-api-via-dashscope
- Gemini/豆包图片依据沿用 a4-image-providers-contract.md。本轮未验证账户权限或收费调用。
