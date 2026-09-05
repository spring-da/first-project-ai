package com.springda.devnest.auth;

import com.springda.devnest.common.TooManyRequestsException;
import com.springda.devnest.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginRateLimiter {

    private final AppProperties properties;
    private final ConcurrentHashMap<String, AttemptWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public LoginRateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    public void check(String normalizedEmail, String remoteAddress) {
        var now = Instant.now();
        var duration = properties.accountSecurity().loginRateLimitWindow();
        var key = normalizedEmail + '\n' + (remoteAddress == null ? "unknown" : remoteAddress);
        var current = windows.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.startedAt().plus(duration).isAfter(now)) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(previous.startedAt(), previous.attempts() + 1);
        });

        if ((requestCount.incrementAndGet() & 255) == 0) {
            windows.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(duration).isAfter(now));
        }

        if (current.attempts() > properties.accountSecurity().loginRateLimitAttempts()) {
            var retryAt = current.startedAt().plus(duration);
            throw new TooManyRequestsException(
                    "登录尝试过于频繁，请稍后重试",
                    retryAt.getEpochSecond() - now.getEpochSecond());
        }
    }

    private record AttemptWindow(Instant startedAt, int attempts) {
    }
}
