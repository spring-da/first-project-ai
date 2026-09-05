package com.springda.devnest.common;

public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;
    private final String code;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        this(message, retryAfterSeconds, "LOGIN_RATE_LIMITED");
    }

    public TooManyRequestsException(String message, long retryAfterSeconds, String code) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.code = code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }
}
