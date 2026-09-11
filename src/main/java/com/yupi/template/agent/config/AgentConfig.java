package com.yupi.template.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 * 提供 Agent 相关的全局配置和共享组件
 *
 * @author AI Passage Creator
 */
@Configuration
@Getter
public class AgentConfig {

    /**
     * 是否启用多智能体编排器
     * true: 使用新的 Spring AI Alibaba 多智能体编排
     * false: 使用原有的 ArticleAgentService
     */
    @Value("${article.agent.orchestrator.enabled:true}")
    private boolean orchestratorEnabled;

    @Value("${article.agent.review-loop.enabled:false}")
    private boolean reviewLoopEnabled;

    @Value("${article.agent.intervention.enabled:false}")
    private boolean interventionEnabled;

    /**
     * Agent 最大迭代次数
     */
    @Value("${article.agent.max-iterations:10}")
    private int maxIterations;

    /**
     * 提供内存状态保存器（单例）
     * 用于 Agent 对话记忆管理
     */
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }
}
