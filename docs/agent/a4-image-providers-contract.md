# A4 图片供应商契约（开发候选，2026-09-11）

本轮在已有演示/本地存储上增加双供应商。代码已接入，已执行专项验证；用户接手新安装、页面全链路、升级和真实体验，完整A4尚未验收；真实调用全部 NOT_RUN，随后由用户自行体验任意一家已持有 Key 的服务。不要沿用 pre-dual-provider 的 ENGINEERING_PASS。

## 选择与配置

| 页面/API method | 实际 provider | 配置 | 协议 |
|---|---|---|---|
| DEMO（默认） | 演示/占位 | 不需要图片凭据 | 受限 Picsum → 本地 PNG，明确 DEGRADED |
| NANO_BANANA（兼容旧名） | gemini | GEMINI_API_KEY、GEMINI_MODEL | Google native generateContent |
| DOUBAO | doubao | DOUBAO_API_KEY、DOUBAO_MODEL | 火山方舟 images/generations |

同一任务只能选择一家真实图片供应商；不自动收费切换。两家均可选，非必填。能力 GET /api/article/image-capabilities 需要登录，只返回配置是否齐全、模型名称、默认模式和 realCallsVerified=false；配置齐全不是账户验证。普通请求不能指定 endpoint/model。可信后端端点目前只允许 Google 官方根地址和北京 Ark /api/v3，不能填代理、内网或任意 URL。

Gemini 默认 gemini-3.1-flash-image，1K、16:9、最大2048输出token。保留旧 IMAGE_MODEL_API_KEY / IMAGE_MODEL 环境别名；原 nano-banana 配置绑定仍有效。豆包不猜测账户模型：DOUBAO_MODEL 留空，用户在方舟控制台复制已开通的 Seedream 生图模型/接入点ID（非聊天模型）。2K、每次请求单图（文章计划可有多个槽）、非stream、b64_json、watermark=true。配置规格需与账户模型实际支持范围一致。

## 持久化与执行

新增可重复增量 sql/add_image_providers.sql：article_image_profile 固定任务 method/provider/model/endpoint/规格/超时，绝不含 Key；article_image_metadata 保存生成结果的非敏感来源/请求ID/usage。创建事务写入快照；恢复/编辑重评/重试使用快照，不跟随全局模型变更。换 Key 是同一家服务的后端配置维护，不改变模型快照。历史文章原图继续查看；没有快照的旧付费任务要新建任务才允许新生成，不自动猜测旧模型。

内部 ImageRequest 包含 prompt、位置、规格及 ImageProfile；ImageData 包含栅格 bytes 和 ImageMetadata(provider/model/requestId/jobId/usage/errorCategory)。结果元数据沿工具、media、文章和 Runtime 结果保存，页面显示来源/模型/usage；usage 不是实际账单。供应商未返回 jobId 或费用时保持 null，响应 requestId 不冒充可查询任务ID。

HTTP 单次提交，禁重定向；15秒连接限制，默认120秒总调用限制（允许1–180秒），响应8MiB、图片5MiB，沿原栅格验证/本地原子存储/鉴权接口/ZIP导出。Runtime 的步骤与整轮期限可能更早截止。已生成 bytes 与 metadata 先入库再保存本地图；存储失败只重试保存，复用同一任务结果。旧 Runtime 关闭路径同样按快照任务ID缓存已收到图片，但不承诺完整中断恢复。

失败响应提供的请求ID经格式校验后保存在Runtime imageDiagnostics，原始响应体不外传。AUTH/QUOTA/INVALID_REQUEST/NO_IMAGE 明确停止本次图片，人工可决定后续操作；TIMEOUT/TRANSPORT/非法成功响应等无法确认外部结果的情况为不确定，Runtime 暂停，禁止自动再次提交。调用期间取消或失去租约仍由原 guard/fence 阻止迟到写入。模型已成功但本地任何留存尚未提交的窗口不能恢复结果：必须人工确认可能重复收费风险。

所选同步接口未接入跨请求幂等键、查询或取消能力。不会套用测试供应商的能力；本地 Future 中断不是供应商取消，仍可能收费。不宣称跨系统 exactly-once。RECHECK_EXTERNAL 不授权再次收费；人工明确重试仍受持久调用/图片/费用预算限制。

## 官方依据与核查范围

- [Google 模型说明](https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-image)：核对正式模型代码；账户地区、开通与余额未验证。
- [Google generateContent](https://ai.google.dev/api/generate-content)、[图片生成指南](https://ai.google.dev/gemini-api/docs/image-generation)：原生 contents/parts、generationConfig、inlineData 和 usageMetadata；x-goog-api-key 鉴权。
- [方舟图片API](https://www.volcengine.com/docs/82379/1541523)、[图片指南](https://www.volcengine.com/docs/82379/1548482)：网站本轮返回SPA壳，未据此宣称完整正文已读取。
- [火山官方SDK图片资源](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkarkruntime/resources/images/images.py)、[响应类型](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkarkruntime/types/images/images.py)：核对 /images/generations、model/prompt/size/response_format、单图disabled和 b64_json/usage。未把社区示例模型视为用户账户可用证明。

## 升级/回退

新库 Compose 初始化新增第10份SQL；旧库先备份并在维护时执行增量。增量只建两张新表，不删除历史行/图片。具体命令见用户实战清单。回退应关闭新任务入口并使用 pre-dual-provider 对应应用构建；新增表可保留，含DOUBAO的新任务不可交给不认识它的旧版本继续执行。不要删除卷或盲目重跑付费任务。

鉴权另核对[火山官方SDK客户端](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkarkruntime/_client.py)：API Key 使用 Authorization Bearer；本实现没有启用其默认重试行为。
