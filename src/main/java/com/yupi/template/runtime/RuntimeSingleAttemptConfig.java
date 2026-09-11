package com.yupi.template.runtime;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetryTemplate;
@Configuration @ConditionalOnProperty(name="article.runtime.enabled",havingValue="true")
public class RuntimeSingleAttemptConfig {
 @Bean public RetryTemplate runtimeRetryTemplate(){return RetryTemplate.builder().maxAttempts(1).build();}
}
