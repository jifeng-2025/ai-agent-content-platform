package com.yupi.template.runtime;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling @Getter
public class RuntimeConfig {
 @Value("${article.runtime.enabled:false}") private boolean enabled;
 @Value("${article.runtime.lease-ms:15000}") private long leaseMs;
 @Value("${article.runtime.step-timeout-ms:60000}") private long stepTimeoutMs;
 @Value("${article.runtime.active-budget-ms:900000}") private long activeBudgetMs;
 @Value("${article.runtime.max-calls:12}") private int maxCalls;
 @Value("${article.runtime.max-image-retries:3}") private int maxImageRetries;
 @Value("${article.runtime.max-estimated-cost-micros:5000000}") private long maxEstimatedCostMicros;
 @Value("${article.runtime.text-reserve-micros:100000}") private long textReserveMicros;
 @Value("${article.runtime.image-reserve-micros:1000000}") private long imageReserveMicros;
 @Value("${article.runtime.event-retention-count:1000}") private int eventRetentionCount;
}
