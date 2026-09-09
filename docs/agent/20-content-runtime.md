# B 内容 Runtime 与可靠执行设计

仅在可靠性、Research/工具集成或跨模块接口任务中读取；仅改 Reviewer 时优先读 10-a-minimum.md。

## 5. 推荐架构：确定性主流程 + 局部自主 Loop

“最新范式”在本项目中指可执行的上下文、工具、反馈、恢复和评测设计，不是一种有统一版本号的行业标准。工作流与自主决策可以混合，不要求每一步都由模型决定。

```text
Vue：创作表单 / 大纲确认 / 证据侧栏 / 版本对比 / 任务轨迹
  ↓ HTTP + SSE（兼容现有事件）
Java：现有账号、文章、配图策略 + Content Runtime
  ↓
需求与受众 → 研究计划 → Research 工具 Loop → 大纲确认
                              ↑                  ↓
                         证据不足 ← Reviewer ← Writer
                                        ↓通过
                            配图策略 → 图文检查 → 人工预览/导出
                                        ↑失败局部重试
  ↓
MySQL：任务、版本、证据、检查点、事件；Redis：会话、锁、短期缓存
云端：文本 / 视觉 / Embedding / 生图；外部：搜索 / MCP / 图库
```

角色先实现成模块或图节点，可共享模型。Research 在预算内选择搜索词、读取资料、判断证据缺口；Reviewer 输出结构化问题；Runtime 校验后决定补检索、改正文、重试配图或交人工处理。

### 5.1 内容运行 Harness

- **State**：runId、userId、phase、draftVersion、evidenceIds、reviewIssues、iteration、budget、toolJobs、lastEventId。状态带 schemaVersion；SSE 连接对象不进入持久化状态。
- **Tools**：先实现 search/read/retrieve/image 四类稳定接口；参数 Schema 校验，结果包含 status、source、error、duration。再把一个已有工具接入 MCP，验证协议适配价值。
- **Context**：每个节点只读取目标、相关证据、当前稿件和当前问题；长网页分段检索、历史摘要、保留来源定位；记录来源抓取时间，不把网页内容当系统指令。
- **Evidence**：来源 URL/文档 ID、标题、片段位置、抓取时间、关联 claimId。程序保证引用可解析，评审与人工抽样判断证据是否真的支持论断；来源存在不等于论断正确。
- **Review**：输出 issues[{type, severity, claimId/sectionId, evidenceIds, action}]；检查受众匹配、精确数字依据、引用、结构、篇幅和配图语义。文本通过后优先生图。
- **Budget**：初始最大修订 2 轮、工具调用 12 次；工具超时按类型区分（生图用异步 jobId）；任务费用上限由用户配置。所有上限由程序执行，不能仅写在 Prompt 中。
- **Resume**：MySQL 保存阶段检查点和外部 jobId。工具调用采用幂等键与结果复用；跨外部 API 不宣称 exactly-once。结果不确定时先查询，不能盲目重新生图扣费。
- **Terminal states**：SUCCEEDED / NEEDS_REVIEW / FAILED / CANCELLED。预算耗尽或证据不足不得假报成功，保留可用正文及失败原因。
- **Observability**：复用 AgentLog，补 runId、stepId、耗时、token、费用估算/实际值标识、错误类别、模型/Prompt 版本。展示行动摘要，不依赖模型内部思维链。
- **图像降级**：现有 Picsum 随机图只能明确标记为占位/降级，不能计为语义匹配成功；支持缺图完成正文和单图重试，保存图片来源信息。
- **边界**：延续登录与任务归属校验；检索限制内部地址，处理恶意网页提示、工具参数异常和生成 Markdown/HTML 注入；测试确认不同用户不可访问彼此资料。发布仍是后续明确授权的功能。

### 5.2 RAG / Memory 的范围

先做小规模用户资料库：解析 → 分块 → 检索 → 附引用，建立固定检索评测集，再比较关键词与向量/混合检索。MySQL 继续保存业务与证据元数据；向量检索通过接口接入，后续按资源和测量结果选轻量实现或单独服务，不为 pgvector 迁移业务数据库。

短期任务状态、压缩历史、长期风格偏好分别保存。长期偏好须可查看、修改、删除；用户修订只是候选反馈，不自动当成事实或训练信号。先证明记忆提升符合度，再增加复杂机制。

执行顺序以 70-roadmap.md 和 status.md 为准：A 的恢复/幂等先于 B 的联网研究上线。
