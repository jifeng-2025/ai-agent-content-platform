# 评审超时修复

只读诊断：该任务QUICK_ADVICE在60.006秒触发60秒截止，账本UNCERTAIN、无返回正文；正文12.758秒和图片41.791秒成功。确认为请求未在期限内完成，不是ReviewJson解析失败；没有供应商响应，无法证明服务端具体耗时原因。未读取Key、未重试收费API，原用户任务未改写。

针对性改动：独立completeReview在已知官方Ark/DeepSeek主机、DeepSeek V4模型时发送thinking.type=disabled，减少额外思考耗时风险；其他模型/自定义端点与正常写作调用保持原请求格式，不自动降级。减少评审中重复图片提示词，保留完整正文和Skill约束。没有增大超时、调用数或费用预算。协议依据：[火山官方SDK Thinking](https://pkg.go.dev/github.com/volcengine/ark-runtime-go/arkruntime/model/chat#Thinking)、[DeepSeek官方思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)。官方Ark网页抓取未成功；SDK确认参数存在，不代表用户账户实测通过。

增加POST /article/quick/{id}/advice-retry，校验登录/归属/状态/预期版本/请求ID去重/费用确认，只允许缺失有效评审的已完成图文任务。新action QUICK_ADVICE_RETRY复用Runtime lease/fence、已固定文字配置、原预算和取消；执行入口直接Q_ADVICE，跳过正文及图片。超时和格式错误分别说明，仍保留图文。界面在创作/详情共用QuickAdvice，显示仅重新评审按钮和费用确认，终态/卸载清理轮询。

测试与编译见compile.json/typecheck.json/tests.json。原服务未重建，无新SQL；用户更新backend/frontend后，在旧文章评审区点击“仅重新评审”，确认可能收费。真实修复效果及浏览器交由用户验证，NOT_RUN，不沿用旧A4 PASS。

最终结果：后端package、前端vue-tsc均退出码0；52项专项Mock测试通过，无失败/跳过，其中评审请求4项、仅评审重试1项、原专项47项。未执行真实API、浏览器或前端完整build，不能宣称真实超时率已经验证改善。
