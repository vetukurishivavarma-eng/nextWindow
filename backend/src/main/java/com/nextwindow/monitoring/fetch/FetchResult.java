package com.nextwindow.monitoring.fetch;

import java.time.Duration;

/**
 * Outcome of retrieving a source. {@code body} is only present on success.
 */
public record FetchResult(Outcome outcome, Integer httpStatus, String body, String contentType, String finalUrl,
                          String message, Duration retryAfter) {

    public enum Outcome {
        SUCCESS(false),
        /** Access refused by the site: robots.txt, 401/403, paywall. Never bypassed. */
        BLOCKED(true),
        RATE_LIMITED(true),
        NOT_FOUND(false),
        HTTP_ERROR(true),
        TIMEOUT(true),
        NETWORK_ERROR(true),
        UNSUPPORTED_CONTENT(false),
        TOO_LARGE(false),
        UNSAFE_URL(false),
        TOO_MANY_REDIRECTS(false);

        private final boolean transientFailure;

        Outcome(boolean transientFailure) { this.transientFailure = transientFailure; }

        public boolean isTransient() { return transientFailure; }
    }

    public boolean isSuccess() { return outcome == Outcome.SUCCESS; }

    public static FetchResult success(int status, String body, String contentType, String finalUrl) {
        return new FetchResult(Outcome.SUCCESS, status, body, contentType, finalUrl, null, null);
    }

    public static FetchResult failure(Outcome outcome, Integer status, String message) {
        return new FetchResult(outcome, status, null, null, null, message, null);
    }

    public static FetchResult rateLimited(Integer status, String message, Duration retryAfter) {
        return new FetchResult(Outcome.RATE_LIMITED, status, null, null, null, message, retryAfter);
    }
}
