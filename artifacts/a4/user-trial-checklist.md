# 用户手动验收：豆包

**新安装、页面全链路、升级、真实豆包调用：交由用户验证。** 代理已执行/失败项目见[报告](report.md)。没有收费调用、提交或推送；只需实测你持有Key的一家，Gemini保持真实调用待验证。

## 1. 本机配置

运行 `node tools/init-local-env.mjs`，本机编辑`.env`（工具不会覆盖已有文件；必须保留旧MySQL密码）。填写 `DASHSCOPE_API_KEY`、`DOUBAO_API_KEY`、`DOUBAO_MODEL`；后者从方舟控制台复制已开通的**Seedream图片模型/接入点ID**，不能填聊天模型。默认端点为`https://ark.cn-beijing.volces.com/api/v3`，图片协议`/images/generations`，模型须支持本适配器2K、b64_json单图参数。

设置 `IMAGE_STORAGE=local`，`REVIEW_LOOP_ENABLED=true`、`INTERVENTION_ENABLED=true`、`RUNTIME_ENABLED=true`。Gemini/COS不必配置。不要把Key发到聊天/页面/截图/Git；文字与图片均可能收费，先设供应商消费提醒，程序预算不等于账单硬封顶。

## 2. 备份、迁移、重建（全部由你执行）

只读现状：原四容器已停止；MySQL/Redis使用下列命名卷；原backend无图片挂载。未检查原库表结构，因此按序补齐五份可重复增量。先冷备，备份目录在仓库外：

~~~powershell
Set-Location D:\2026codex\ai-passage-creator
$backupDir = 'D:\2026codex\private-backups\a4-' + (Get-Date -Format yyyyMMdd-HHmmss)
New-Item -ItemType Directory -Path $backupDir | Out-Null
foreach ($serviceName in @('backend','frontend','mysql','redis')) {
  if ((docker inspect "ai-passage-$serviceName" --format '{{.State.Running}}') -eq 'true') { throw '先结束任务并停止原服务，不能冷备运行中的数据库' }
  if ($LASTEXITCODE -ne 0) { throw '原容器不存在，先核对环境' }
}
$oldBackend = docker inspect ai-passage-backend --format '{{.Image}}'
docker image tag $oldBackend ai-passage-backend:a4-before-dual
docker image tag (docker inspect ai-passage-frontend --format '{{.Image}}') ai-passage-frontend:a4-before-dual
$backupVolumes = @('ai-passage-mysql-data','ai-passage-redis-data')
docker volume inspect ai-passage-image-data *> $null
if ($LASTEXITCODE -eq 0) { $backupVolumes += 'ai-passage-image-data' }
foreach ($volumeName in $backupVolumes) {
  docker run --rm --network none --user 0 --entrypoint sh --mount "type=volume,source=$volumeName,target=/source,readonly" --mount "type=bind,source=$backupDir,target=/backup" $oldBackend -c "tar -czf /backup/$volumeName.tgz -C /source ."
  if ($LASTEXITCODE -ne 0) { throw "备份失败：$volumeName，停止升级" }
}
Copy-Item -LiteralPath .env -Destination (Join-Path $backupDir '.env.local-backup')
docker compose up -d mysql redis
docker compose ps
# 等mysql/redis均healthy后，再执行：
$upgradeScripts = @('add_article_review.sql','add_article_intervention.sql','add_article_runtime.sql','add_local_image_storage.sql','add_image_providers.sql')
foreach ($scriptName in $upgradeScripts) {
  Get-Content -Raw (Join-Path 'sql' $scriptName) | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
  if ($LASTEXITCODE -ne 0) { throw "迁移失败：$scriptName；停止升级" }
}
docker compose up -d --build --no-deps backend frontend
docker compose ps
~~~

上述用户环境命令本轮未执行。新空环境初始化配置后用`docker compose up -d --build`；不要`down -v`。启动后核对旧文章可读、新图片卷挂载`/data/images`。

## 3. 一次页面实战

默认`http://localhost`登录 → 新建约500字、一个章节、要求一张封面 → **明确选择豆包** → 标题 → 大纲 → 评审/版本；必要时阅读问题后人工接受或编辑重评 → 配图合成 → ZIP导出，离线解压打开`index.html`。

成功标准：provider=**doubao**且model符合选择；真实图片不是DEMO/占位，图文对应；评审/人工决定保留；刷新、无任务时重建backend后图片仍可读；ZIP离线可看；旧文章不丢失。单图重试保留正文，可能收费，不要无故点击。

每次请求单图，但整篇可能规划多个槽；“一张封面”提示词不是账单上限。默认Runtime限12调用、3次图片重试、15分钟活动时间、单步60秒。修订上限/外部不确定必须有停止或人工出口，不能用占位冒充真实生图通过。

## 4. 错误、证据与回退

记录taskId、时间、provider/model、状态、脱敏错误类别、requestId、usage；页面评审面板或登录后的`/api/article/{taskId}/runtime`之`imageDiagnostics`可查。usage/账单未知记UNKNOWN。成功保存截图和离线ZIP；失败保存上述诊断，不保存Key、Cookie、请求头或完整供应商响应。不确定时先查方舟控制台，不重复收费提交。

`docker compose logs --tail=200 backend`只在本机排查，旧业务日志可能含正文，**不要原样上传**；优先提供脱敏接口诊断。供应商不能取消时，本地取消仍可能收费。

回退：结束/取消新任务，三个新开关改false，保留数据库新表与图片卷；旧版本不得续跑DOUBAO任务。使用已备份的应用镜像，不覆盖数据库：

~~~powershell
@('services:','  backend:','    image: ai-passage-backend:a4-before-dual','  frontend:','    image: ai-passage-frontend:a4-before-dual') | Set-Content (Join-Path $backupDir 'rollback.yml')
docker compose -f docker-compose.yml -f (Join-Path $backupDir 'rollback.yml') up -d --no-build --no-deps backend frontend
~~~

旧镜像不保证显示新本地图/新供应商。数据保留不等于旧界面功能兼容；数据库备份恢复是另行确认的操作。

## 5. 实战后提交前

记录本次源码/构建指纹和所测一家结果，另一家未验证；复核`submission-files.txt`与diff，排除`.env`、备份、用户图片/数据库卷、ZIP、JAR/dist、缓存和未脱敏日志；取得对应上游版本的公开分发授权/署名依据（当前UNRESOLVED，不自行补许可证）。origin应为`jifeng-2025/ai-agent-content-platform`，不向upstream推送。你明确确认后才commit/push，不强推或自动Release。

建议提交说明：`feat: add persistent content runtime and optional Gemini/Seedream images`。清单包含尚未提交的A3/A4，不应只提交本轮适配器。
