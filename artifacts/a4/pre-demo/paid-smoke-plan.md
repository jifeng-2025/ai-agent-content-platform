# 本地存图候选真实 API 冒烟（NOT_RUN）

本轮不授权收费。先完成新候选全部免费门禁，再集中申请明确授权。旧COS存储候选的PASS/付费准备不能替代新版本验收。

默认只需两个外部凭据：DASHSCOPE_API_KEY（百炼文字）与 IMAGE_MODEL_API_KEY（Google Gemini原生图片API）。图片保存在本机持久卷，不需要COS桶或Key。钥匙仅在忽略的本地.env/后端配置中保存，不在聊天粘贴。模型与端点必须匹配当前账户；配置存在不等于模型已开通。

拟用一份500–700字短文“周末散步中的小发现”，一个确认章节、一张1K配图，qwen-plus非思考 + gemini-3-pro-image。正常5次文本+1次图片；最多两次局部修订，最多9次文本+1次图片。不自动重评新轮次、换模型、重试未知收费结果或增加预算。转人工时保留问题，经真实人工操作接受后继续，不伪造自动通过。

隔离配置：max-calls=10、max-image-retries=0、max-estimated-cost-micros=1000000、text-reserve-micros=10000、image-reserve-micros=900000；9文本+1图预留990000，第二次图片提交超预算。程序预留是保守执行配额，不是账单价格。文字max-tokens=2000，Gemini max-output-tokens=2048、image-size=1K。输入目标<=4000 token不是模型硬输入上限，收费前核对实际请求参数与免费限制验证。供应商本身可能有文本/思考或用量计费，不能宣称硬账单封顶。

[百炼价格](https://help.aliyun.com/zh/model-studio/model-pricing)按地区不同：全球低输入档非思考约0.0648元（9×4000输入、9×2000输出）；国际同用量约0.2646元。[Gemini官方价格](https://ai.google.dev/gemini-api/docs/pricing#gemini-3-pro-image)标准1K图片约US$0.134，另加文字/思考用量。预计短样本约US$0.15–0.30，建议申请US$1等值预算；实际账单未知标null/UNKNOWN，不以估算当实际值。执行前重新核对官方模型目录和账户地区，不默换preview/其他模型。

获准后使用最终已验收JAR，不加载Mock供应商插件；独立MySQL/Redis/图片卷，从页面创建至评审、图片本地保存、合成、查看、Markdown与ZIP导出。检查真实图文匹配、下载后的本地图片和离线内容。保存脱敏请求/usage、生成留存、存储/导出SHA256、截图和候选指纹；原用户服务不动。

存储失败优先复用已生成字节，不重新收费生成；结果无法确认则暂停。新业务代码任何修改都需重冻并补齐受影响测试/最终门禁。分发许可单独核实，不据此阻断本地验收。

最终仅需用户告知两个Key的本机配置位置、确认账户地区/模型并授权一个样本预算；图片Key缺失时保持NOT_RUN，不使用占位图冒充通过。

2026-09-10免费复核：Google官方模型页列出的稳定代码为 gemini-3-pro-image（https://ai.google.dev/gemini-api/docs/models/gemini-3-pro-image）；官方标准价格页再次确认1K/2K图片US$0.134、输入US$2/M token、文本/思考输出US$12/M token（https://ai.google.dev/gemini-api/docs/pricing?authuser=1）。这里只确认公开目录/价格，不代表当前账户权限或可用额度。
