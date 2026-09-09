package com.yupi.template.review;

import com.yupi.template.controller.ArticleController;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.entity.*;
import com.yupi.template.repository.ReviewTraceStore;
import com.yupi.template.service.*;
import com.yupi.template.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.yupi.template.review.A1ReviewLoopTest.*;
import static com.yupi.template.model.dto.article.ReviewResult.*;

class A1PersistenceTest {
    ArticleServiceImpl service;
    ReviewTraceStore store;
    Article article;
    @BeforeEach void setup() {
        service=spy(new ArticleServiceImpl());store=mock(ReviewTraceStore.class);
        ReflectionTestUtils.setField(service,"reviewTraceStore",store);
        article=new Article();article.setId(7L);article.setTaskId("a1-fixed");article.setUserId(1L);
        doReturn(article).when(service).getByTaskId("a1-fixed");doReturn(true).when(service).updateById(any(Article.class));
    }
    @Test void humanDraftReadableAndNotCompleted() {
        var s=state();run(s,new Fixed(review(Decision.NEEDS_REVIEW,Type.EVIDENCE_REQUIRED)));
        service.saveReviewProgress("a1-fixed",s);
        assertEquals("NEEDS_REVIEW",article.getStatus());assertEquals("NEEDS_REVIEW",article.getPhase());
        assertEquals(ORIGINAL,article.getContent());assertEquals(ORIGINAL,article.getFullContent());assertNull(article.getCompletedTime());
        verify(store).save("a1-fixed",s.getReviewTrace());
        var user=new User();user.setId(1L);when(store.find("a1-fixed")).thenReturn(s.getReviewTrace());
        assertEquals(s.getReviewTrace(),service.getArticleReview("a1-fixed",user));
        assertEquals("NEEDS_REVIEW",service.getArticleDetail("a1-fixed",user).getStatus());
    }
    @Test void deniedUserCannotReadVersions() {
        var user=new User();user.setId(2L);user.setUserRole("user");
        assertThrows(BusinessException.class,()->service.getArticleReview("a1-fixed",user));verifyNoInteractions(store);
    }
    @Test void adminCanReadVersions() {
        var user=new User();user.setId(2L);user.setUserRole("admin");service.getArticleReview("a1-fixed",user);verify(store).find("a1-fixed");
    }
    @Test void updateFailureThrowsInsideRequiredTransaction() throws Exception {
        var s=state();run(s,new Fixed(PASS));doReturn(false).when(service).updateById(any(Article.class));
        assertThrows(IllegalStateException.class,()->service.saveReviewProgress("a1-fixed",s));
        var tx=ArticleServiceImpl.class.getMethod("saveReviewProgress",String.class,ArticleState.class).getAnnotation(Transactional.class);
        assertNotNull(tx);assertEquals(List.of(Exception.class),Arrays.asList(tx.rollbackFor()));
    }
    @Test void controllerUsesAuthenticatedServiceRead() {
        var controller=new ArticleController();var users=mock(UserService.class);var articles=mock(ArticleService.class);
        ReflectionTestUtils.setField(controller,"userService",users);ReflectionTestUtils.setField(controller,"articleService",articles);
        var request=new MockHttpServletRequest();var user=new User();user.setId(1L);when(users.getLoginUser(request)).thenReturn(user);
        var s=state();run(s,new Fixed(PASS));when(articles.getArticleReview("a1-fixed",user)).thenReturn(s.getReviewTrace());
        assertEquals(s.getReviewTrace(),controller.getReview("a1-fixed",request).getData());verify(users).getLoginUser(request);
    }
}
