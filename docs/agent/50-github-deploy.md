# GitHub 交付与简单部署
仅 G0/D1、推送、PR、CI、部署任务读取。A4仅准备候选发布与隔离Compose验收；CI/GHCR发布/服务器部署仍未实现。

## 当前远端状态（2026-09-10）

本机实查 origin 为 https://github.com/jifeng-2025/ai-agent-content-platform.git，upstream 为 https://github.com/yuyuanweb/ai-passage-creator.git。G0已完成，以下首次绑定检查是历史记录，不应重复执行remote修改。A4不提交、不推送、不创建Release。

## G0之前的历史检查（2026-09-09，已被上方状态替代）

- GitHub MCP 已可调用，get_profile 和 list_repositories 成功；账号为 jifeng-2025。当前可读列表中5个仓库未包含本图文平台；不因此断言账号下绝无其他未授权仓库。
- 本地 origin（fetch/push）仍是 https://github.com/yuyuanweb/ai-passage-creator.git，属于上游；未修改远端。
- MCP 提供仓库、提交、PR和CI相关能力；目标项目写权限尚未验证。不得把其他仓库的push权限当成本项目权限。
- MCP会话连接不自动配置本地Git HTTPS/SSH认证。PATH未找到gh；Git凭证可独立存在，当前未验证，后续使用系统凭证管理器或SSH，不把token写进命令/remote/.env。
- .env已被gitignore排除且未跟踪；推送前还须检查实际暂存差异与拟提交历史。忽略规则不能清理已经提交的历史密钥。

## G0：首次绑定自己的仓库（历史流程，已完成）

以下为绑定前的历史流程；当前URL/公开可见性已确定，勿重复执行。原始前提：目标URL和可见性尚未确定。用户提供现有URL，或明确选择Fork/新仓库和可见性后执行；不默认发布为公开仓库。

推荐保留上游历史并Fork；若需要独立私有开发，按上游实际许可证建立新仓库并保留署名，先核验许可证文本，不能只依据README徽章。

顺序：
1. MCP核验目标owner/repo、当前账号写权限、默认分支及是否已有内容；已存在历史的仓库先比较，禁止直接覆盖。
2. 核对本地remote后将现有origin保留为upstream，把用户仓库设为origin；不自动猜仓库名。
3. 本地Git验证认证和只读连接；在该任务获准上传后，以功能分支执行首次push，再由MCP核验远端commit SHA与本地一致。
4. 将真实URL、权限验证结果和分支策略写入status.md。不得保存凭证。
5. 新聊天先检查GitHub工具是否仍可用；如不可用，说明连接需要恢复，不因本文记录就假定永远在线。

绑定后的典型命令模板（占位符必须替换；本轮未执行）：
```sh
git remote rename origin upstream
git remote add origin <已核验的个人仓库URL>
git push -u origin <当前功能分支>
```
已有remote配置不重复rename/add，先读状态；保留上游远端仅用于同步，不向其推送。

## 日常上传

用户说“上传本次改动到我的仓库”且已绑定目标后：
检查差异/敏感信息 → 运行该变更必需检查 → 仅暂存本次文件 → 提交 → push功能分支 → MCP核验SHA；需要PR时创建并查看CI。
代码存档可以记录未通过的检查，**发布版本必须通过门禁**。不得force push、改写历史或把无关文件一并上传。“可以上传”不等于每轮自动推送。

Git负责本地批量文件、二进制与历史；MCP用于PR、Issue、CI和远端核验，避免用逐文件API替代常规Git开发。

## 已有部署能力

根docker-compose.yml运行frontend/backend/mysql/redis，前端80、后端8123；两份Dockerfile已包含构建。Nginx已有SSE关闭buffering与300s读取超时。MySQL/Redis有持久卷。

现有本地启动/更新入口（从项目根目录运行，.env已配置）：
```sh
docker compose up -d --build
docker compose ps
```
这会重建/重启服务，应在当前创作任务完成后执行。新数据库迁移需要另行运行；初始化SQL挂载只在空数据库初始化时生效。

后端Docker构建目前使用-DskipTests，不能把镜像构建成功当成测试通过。前端build含类型检查；lint当前会--fix，CI需要独立只读命令。

## D1：推荐发布路线

**GitHub → CI测试 → GHCR版本镜像 → 单台Linux服务器Docker Compose**。本地继续用原Compose。GPU和Kubernetes均非必要；GitHub Pages不能承载Java/MySQL后端。

拟新增：
- .github/workflows/ci.yml：后端独立测试（测试配置/临时MySQL、Redis或隔离替身），npm ci、只读lint、type-check、build、固定响应E2E；无需真实模型密钥。
- .github/workflows/release.yml：发布tag或手动触发，门禁通过后构建前后端镜像；使用明确Git SHA/tag和digest，不以latest作为唯一回滚依据。
- deploy/compose.release.yml：完整的发布Compose，应用使用GHCR image，数据库/缓存持久化；支持变量镜像版本，且移除/调整固定container_name/network/volume名以隔离环境。
- deploy/.env.example：仅占位项；真实服务器配置独立保存。
- scripts/deploy/：环境预检、应用更新、健康检查、备份与回滚入口（后续实现，当前不存在）。

服务器更新目标（待release文件与镜像落地后才可执行）：
```sh
docker compose --env-file deploy/.env -f deploy/compose.release.yml pull
docker compose --env-file deploy/.env -f deploy/compose.release.yml up -d --no-build
```

执行前核对发布版本、数据库迁移兼容性和备份。公网仅暴露必要入口，配置域名/TLS、实际认证与配额；数据库不开放公网。MCP账号授权、Actions GITHUB_TOKEN发布权限、服务器拉取私有GHCR凭证是三件不同的事。

CI对外部PR使用只读权限和固定响应，不提供生产密钥；镜像发布只在可信分支/tag上下文，按需要授予packages:write。生产发布先采用手动触发，不把合并PR等同上线。

## 部署验收与回滚

- 干净环境按文档启动成功；健康接口、登录、创作、SSE流式、重连、图片可访问均验证。
- 迁移为增量、可追踪执行；应用回滚不等于数据库回滚，变更须向后兼容或提供经过验证的恢复流程。
- 切回上一个固定镜像版本，验证文章/用户数据不丢失。不运行docker compose down -v清空卷。
- Windows/WSL脚本入口只选一种环境执行；Linux服务器文档独立说明路径；Docker磁盘占用与缓存按16GB电脑资源实测。
- 服务器地址、资源和域名尚未指定。D1完成标准是可复现部署包与目标环境实际验收，不能仅凭YAML存在声称已上线。

官方依据：[Docker Compose生产部署](https://docs.docker.com/compose/how-tos/production/)、[GitHub Actions发布镜像](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)。

历史A4演示候选（已归档pre-dual-provider）：单文字Key候选工程门禁已通过；真实文字授权未齐，真实AI生图按用户决定延后，分发许可未明确，见 [许可核查](../../artifacts/a4/license-review.md) 和 [冒烟准备](../../artifacts/a4/paid-smoke-plan.md)。既往G0首次推送不等于授权本轮自动上传。

2026-09-11双供应商候选：Gemini/豆包适配代码已加入，专项结果见A4报告，用户接手安装/页面/升级/真实体验；此前单文字Key演示PASS已归档。真实体验由用户随后选择一家完成，另一家NOT_RUN；不自动提交/推送/部署。增量为sql/add_image_providers.sql，详见A4用户实战清单。

## 2026-09-11 最新授权
用户本轮已明确要求修改README并推送当前改造到个人origin；上述A4“不自动提交/推送”是之前轮次范围，不阻止此次授权操作。保留当前main分支正常推送，禁止强推、upstream推送、tag/Release及部署。完整A4/分发许可仍不冒充通过。
