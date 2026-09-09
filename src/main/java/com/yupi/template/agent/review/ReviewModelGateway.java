package com.yupi.template.agent.review;

public interface ReviewModelGateway {
    String complete(String prompt);
    final class CallFailure extends RuntimeException {
        private final boolean timeout;
        public CallFailure(boolean timeout) {
            super(timeout ? "Review model timed out" : "Review model request failed");
            this.timeout = timeout;
        }
        public boolean isTimeout() { return timeout; }
    }
}
