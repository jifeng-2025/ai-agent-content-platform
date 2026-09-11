package com.yupi.template.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.manager.SseEmitterManager;
import com.yupi.template.model.dto.article.ArticleAiModifyOutlineRequest;
import com.yupi.template.model.dto.article.ArticleConfirmOutlineRequest;
import com.yupi.template.model.dto.article.ArticleConfirmTitleRequest;
import com.yupi.template.model.dto.article.ArticleCreateRequest;
import com.yupi.template.model.dto.article.ArticleQueryRequest;
import com.yupi.template.model.dto.article.ArticleState;

import java.util.List;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.ArticleStyleEnum;
import com.yupi.template.model.vo.AgentExecutionStats;
import com.yupi.template.model.vo.ArticleVO;
import com.yupi.template.service.AgentLogService;
import com.yupi.template.service.ArticleAsyncService;
import com.yupi.template.service.ArticleService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 文章接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource private com.yupi.template.config.NanoBananaConfig imageConfig;
    @Resource private com.yupi.template.config.DoubaoConfig doubaoConfig;
    @Resource private com.yupi.template.modelconfig.ModelSettings modelSettings;
    @GetMapping("/text-capabilities")
    public BaseResponse<java.util.Map<String,Object>> textCapabilities(HttpServletRequest request){userService.getLoginUser(request);var text=modelSettings.selection("TEXT",null);return ResultUtils.success(java.util.Map.of("name",text.name(),"model",text.model(),"protocol",text.protocol()));}
    @GetMapping("/image-capabilities")
    public BaseResponse<java.util.Map<String,Object>> imageCapabilities(HttpServletRequest request) {
        userService.getLoginUser(request);
        var g=modelSettings.selection("IMAGE","gemini");var d=modelSettings.selection("IMAGE","doubao");
        return ResultUtils.success(java.util.Map.of("defaultMethod",modelSettings.defaultImage(),"geminiConfigured",g!=null||imageConfig.configured(),"doubaoConfigured",d!=null||doubaoConfig.configured(),"geminiModel",g==null?imageConfig.getModel():g.model(),"doubaoModel",d==null?doubaoConfig.getModel():d.model(),"realCallsVerified",false,"demoNotice","演示/占位，非AI生图，不保证语义匹配"));
    }

    @GetMapping("/{taskId}/review")
    @Operation(summary = "获取已保存的评审与草稿版本")
    public BaseResponse<com.yupi.template.model.dto.article.ReviewTrace> getReview(
            @PathVariable String taskId, HttpServletRequest request) {
        return ResultUtils.success(articleService.getArticleReview(taskId, userService.getLoginUser(request)));
    }

    @Resource
    private ArticleService articleService;

    @Resource private com.yupi.template.runtime.RuntimeIntake runtime;
    @Resource private com.yupi.template.runtime.RuntimeEvents runtimeEvents;

    @Resource
    private ArticleAsyncService articleAsyncService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserService userService;

    @Resource
    private AgentLogService agentLogService;

    /**
     * 创建文章任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "选题不能为空");
        // 校验风格参数（允许为空）
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()),
                ErrorCode.PARAMS_ERROR, "无效的文章风格");

        User loginUser = userService.getLoginUser(httpServletRequest);

        if (runtime != null && runtime.enabled()) return ResultUtils.success(runtime.create(request, loginUser));

        // 检查并消耗配额 + 创建文章任务（在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(), 
                request.getStyle(), 
                request.getEnabledImageMethods(),
                loginUser
        );

        // 异步执行阶段1：生成标题方案
        articleAsyncService.executePhase1(
                taskId, 
                request.getTopic(),
                request.getStyle()
        );

        return ResultUtils.success(taskId);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度(SSE)")
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        // 校验权限（内部会检查任务是否存在以及用户是否有权限访问）
        User loginUser = userService.getLoginUser(httpServletRequest);
        articleService.getArticleDetail(taskId, loginUser);

        if (runtimeEvents != null && runtimeEvents.handles(taskId)) {
            String value = httpServletRequest.getHeader("Last-Event-ID");
            if (value == null) value = httpServletRequest.getParameter("cursor");
            long cursor = 0;
            try { if (value != null) cursor = Long.parseLong(value); } catch (NumberFormatException e) { throw new com.yupi.template.exception.BusinessException(ErrorCode.PARAMS_ERROR,"事件游标无效"); }
            ThrowUtils.throwIf(cursor < 0, ErrorCode.PARAMS_ERROR,"事件游标无效");
            return runtimeEvents.open(taskId,cursor);
        }
        // 创建 SSE Emitter
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);
        
        log.info("SSE 连接已建立, taskId={}", taskId);
        return emitter;
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);

        return ResultUtils.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    public BaseResponse<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest request,
                                                       HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);
        
        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, 
                ErrorCode.PARAMS_ERROR);
        
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);
        
        return ResultUtils.success(result);
    }

    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    @Operation(summary = "确认标题并输入补充描述")
    public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request,
                                            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "副标题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        if (runtime != null && runtime.enabled()) { runtime.title(request, loginUser); return ResultUtils.success(null); }

        // 确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        // 异步执行阶段2：生成大纲
        articleAsyncService.executePhase2(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    @Operation(summary = "确认大纲")
    public BaseResponse<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR, "大纲不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        if (runtime != null && runtime.enabled()) { runtime.outline(request, loginUser); return ResultUtils.success(null); }

        // 确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );

        // 异步执行阶段3：生成正文+配图
        articleAsyncService.executePhase3(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @RequestBody ArticleAiModifyOutlineRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "修改建议不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // AI 修改大纲
        ThrowUtils.throwIf(runtime != null && runtime.enabled(), ErrorCode.CONFLICT_ERROR,"可靠执行模式请直接编辑大纲；自动改纲尚未接入持久预算");
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );

        return ResultUtils.success(modifiedOutline);
    }

    /**
     * 获取任务执行日志
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        
        AgentExecutionStats stats = agentLogService.getExecutionStats(taskId);
        return ResultUtils.success(stats);
    }
}
