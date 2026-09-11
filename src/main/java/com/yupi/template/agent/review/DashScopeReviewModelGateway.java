package com.yupi.template.agent.review;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class DashScopeReviewModelGateway implements ReviewModelGateway {
    private final ChatModel model;
    private final Duration timeout;
    public DashScopeReviewModelGateway(ChatModel model,
            @Value("${article.agent.review-loop.timeout-ms:45000}") long timeoutMs) {
        this.model = model;
        this.timeout = Duration.ofMillis(Math.max(1, Math.min(60000, timeoutMs)));
    }
    @Override
    public String complete(String prompt) {
        try {
            StringBuilder response = model.stream(new Prompt(prompt)).reduce(new StringBuilder(), (text, chunk) -> {
                String value = chunk.getResult().getOutput().getText();
                if (value != null) text.append(value);
                if (text.length() > 64000) throw new ReviewJson.InvalidOutput();
                return text;
            }).block(timeout);
            if (response == null) throw new IllegalStateException("Empty response");
            return response.toString();
        } catch (ReviewJson.InvalidOutput e) { throw e; }
        catch (RuntimeException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof TimeoutException) throw new CallFailure(true);
                cause = cause.getCause();
            }
            throw new CallFailure(false);
        }
    }
}
