package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.mapper.ArticleMapper;
import com.yupi.template.model.dto.article.ArticleQueryRequest;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.entity.Article;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.ArticlePhaseEnum;
import com.yupi.template.model.enums.ArticleStatusEnum;
import com.yupi.template.model.vo.ArticleVO;
import com.yupi.template.service.ArticleAgentService;
import com.yupi.template.service.ArticleService;
import com.yupi.template.service.QuotaService;
import com.yupi.template.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.yupi.template.model.enums.ImageMethodEnum;

import static com.yupi.template.constant.UserConstant.ADMIN_ROLE;
import static com.yupi.template.constant.UserConstant.VIP_ROLE;

/**
 * 文章服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Resource
    private com.yupi.template.repository.ReviewTraceStore reviewTraceStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReviewProgress(String taskId, ArticleState state) {
        com.yupi.template.runtime.RuntimeScope.guard();
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        var trace = java.util.Objects.requireNonNull(state.getReviewTrace());
        reviewTraceStore.save(taskId, trace);
        article.setContent(state.getContent());
        article.setFullContent(state.getContent());
        boolean human = "NEEDS_REVIEW".equals(trace.status());
        article.setStatus(human ? "NEEDS_REVIEW" : "PROCESSING");
        article.setPhase("PASS".equals(trace.status()) ? "CONTENT_GENERATING" : trace.status());
        article.setErrorMessage(human ? trace.stopReason() : null);
        if (!updateById(article)) throw new IllegalStateException("草稿保存失败");
        var runtime = com.yupi.template.runtime.RuntimeScope.current();
        if (runtime != null) runtime.store().checkpoint(runtime.ticket(), human ? "DONE" : "PASS".equals(trace.status()) ? "MEDIA" : "REVIEW", "REVIEW_UPDATED");
    }

    @Override
    public com.yupi.template.model.dto.article.ReviewTrace getArticleReview(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        checkArticlePermission(article, loginUser);
        return reviewTraceStore.find(taskId);
    }

    @Resource
    private QuotaService quotaService;

    @Resource
    private ArticleAgentService articleAgentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 处理配图方式：如果用户未选择，给普通用户设置默认的非 VIP 方式
        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);
        
        // 校验配图方式权限（普通用户不能使用 SVG_DIAGRAM；自托管图片模型对登录用户可用）
        validateImageMethods(finalImageMethods, loginUser);

        // 生成任务ID
        String taskId = IdUtil.simpleUUID();

        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStyle(style);
        article.setEnabledImageMethods(finalImageMethods != null && !finalImageMethods.isEmpty() 
                ? GsonUtils.toJson(finalImageMethods) : null);
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setPhase(ArticlePhaseEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());

        this.save(article);
        modelSettings.snapshot(taskId);
        imageProfiles.save(taskId, finalImageMethods);

        log.info("文章任务已创建, taskId={}, userId={}, style={}", taskId, loginUser.getId(), style);
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 在同一事务中：先扣配额，再创建任务
        // 如果任务创建失败，配额会自动回滚
        quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, style, enabledImageMethods, loginUser);
    }

    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(
                QueryWrapper.create().eq("taskId", taskId)
        );
    }

    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        return ArticleVO.objToVo(article);
    }

    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getPageNum();
        long size = request.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);

        // 非管理员只能查看自己的文章
        if (!ADMIN_ROLE.equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId", loginUser.getId());
        } else if (request.getUserId() != null) {
            queryWrapper.eq("userId", request.getUserId());
        }

        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO
        return convertToVOPage(articlePage);
    }

    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        // 逻辑删除
        return this.removeById(id);
    }

    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);

        log.info("文章状态已更新, taskId={}, status={}", taskId, status.getValue());
    }

    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());
        
        // 保存封面图 URL（从 images 列表中提取 position=1 的 URL）
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            ArticleState.ImageResult cover = state.getImages().stream()
                .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                .findFirst()
                .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章保存成功, taskId={}", taskId);
    }

    /**
     * 校验文章权限
     *
     * @param article   文章
     * @param loginUser 当前用户
     */
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) &&
                !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 将文章分页结果转换为 VO 分页
     *
     * @param articlePage 文章分页
     * @return VO 分页
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }

    @Override
    public void confirmTitle(String taskId, String mainTitle, String subTitle, String userDescription, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 TITLE_SELECTING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.TITLE_SELECTING,
                ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户选择的标题和补充描述
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认标题, taskId={}, mainTitle={}", taskId, mainTitle);
    }

    @Override
    public void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING,
                ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户编辑后的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认大纲, taskId={}, sectionsCount={}", taskId, outline.size());
    }

    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum phase) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setPhase(phase.getValue());
        this.updateById(article);
        log.info("文章阶段已更新, taskId={}, phase={}", taskId, phase.getValue());
    }

    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setTitleOptions(GsonUtils.toJson(titleOptions));
        this.updateById(article);
        log.info("标题方案已保存, taskId={}, optionsCount={}", taskId, titleOptions.size());
    }

    @Override
    public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验 VIP 权限（普通用户不能使用 AI 修改大纲）
        ThrowUtils.throwIf(!isVipOrAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, 
                "AI 修改大纲功能仅限 VIP 会员使用");

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING,
                ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 获取当前大纲
        List<ArticleState.OutlineSection> currentOutline = GsonUtils.fromJson(
                article.getOutline(),
                new TypeToken<List<ArticleState.OutlineSection>>(){}
        );

        // 调用 AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleAgentService.aiModifyOutline(
                article.getMainTitle(),
                article.getSubTitle(),
                currentOutline,
                modifySuggestion
        );

        // 保存修改后的大纲
        article.setOutline(GsonUtils.toJson(modifiedOutline));
        this.updateById(article);

        log.info("AI修改大纲完成, taskId={}, sectionsCount={}", taskId, modifiedOutline.size());
        return modifiedOutline;
    }

    /**
     * 处理配图方式
     * 如果用户未选择，给普通用户设置默认的非 VIP 方式，VIP 用户不限制
     */
    private List<String> processImageMethods(List<String> enabledImageMethods, User loginUser) {
        // 如果用户已选择，直接返回
        if (enabledImageMethods != null && !enabledImageMethods.isEmpty()) {
            return enabledImageMethods;
        }

        // Explicit no-key demo default; optional models require configuration.
        return List.of(modelSettings.defaultImage());
    }

    /**
     * 校验配图方式权限
     * 所有用户的Gemini选择均检查配置；SVG保留VIP权限
     */
    @jakarta.annotation.Resource private com.yupi.template.config.NanoBananaConfig imageConfig;

    @Resource private com.yupi.template.config.DoubaoConfig doubaoConfig;
    @Resource private com.yupi.template.service.image.ImageProfileStore imageProfiles;
    @Resource private com.yupi.template.modelconfig.ModelSettings modelSettings;

    private void validateImageMethods(List<String> enabledImageMethods, User loginUser) {
        if (enabledImageMethods != null) {
            for (String value : enabledImageMethods) {
                var method = ImageMethodEnum.getByValue(value);
                if (method == null || (method.isFallback() && method != ImageMethodEnum.DEMO)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许的配图方式");
                if (method == ImageMethodEnum.DOUBAO && (modelSettings.selection("IMAGE","doubao")==null && (doubaoConfig == null || !doubaoConfig.configured()))) throw new BusinessException(ErrorCode.PARAMS_ERROR, "豆包未配置Key和模型ID，请选择演示模式");
                if (method == ImageMethodEnum.NANO_BANANA && (modelSettings.selection("IMAGE","gemini")==null && (imageConfig == null || !imageConfig.configured()))) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Gemini 未配置，请选择演示/占位模式");
            }
            if (enabledImageMethods.stream().anyMatch(com.yupi.template.service.image.ImageProfile::paid) && enabledImageMethods.size()!=1) throw new BusinessException(ErrorCode.PARAMS_ERROR,"每个任务只能明确选择一家图片供应商");
            if (enabledImageMethods.contains("DEMO") && enabledImageMethods.size()!=1) throw new BusinessException(ErrorCode.PARAMS_ERROR, "演示模式不能与其他配图方式混选");
        }
        if (enabledImageMethods == null || enabledImageMethods.isEmpty()) {
            return;
        }

        // VIP 和管理员无限制
        if (isVipOrAdmin(loginUser)) {
            return;
        }

        // 普通用户限制
        for (String method : enabledImageMethods) {
            if (ImageMethodEnum.SVG_DIAGRAM.getValue().equals(method)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, 
                        "SVG 图表功能仅限 VIP 会员使用");
            }
        }
    }

    /**
     * 判断是否为 VIP 或管理员
     */
    private boolean isVipOrAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole()) || 
               VIP_ROLE.equals(user.getUserRole());
    }
}
