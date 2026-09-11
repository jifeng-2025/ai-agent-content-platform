package com.yupi.template.modelconfig;
/** Never log parser exceptions: malformed JSON may contain a newly submitted credential. */
@org.springframework.web.bind.annotation.RestControllerAdvice(assignableTypes=ModelAdminController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ModelAdminErrors {
 @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
 public com.yupi.template.common.BaseResponse<?> handle(Exception error){
  if(error instanceof com.yupi.template.exception.BusinessException e)return com.yupi.template.common.ResultUtils.error(e.getCode(),e.getMessage());
  if(error instanceof org.springframework.dao.DataAccessException)return com.yupi.template.common.ResultUtils.error(50001,"模型配置数据库操作失败：请确认已迁移并刷新重试；不要重复收费测试");
  return com.yupi.template.common.ResultUtils.error(40000,"模型设置请求格式或状态无效；敏感请求内容不记录日志");
 }
}
