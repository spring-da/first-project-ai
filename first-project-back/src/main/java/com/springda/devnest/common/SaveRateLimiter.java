package com.springda.devnest.common;

import com.springda.devnest.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** A small per-account guard for keyboard-driven document and snippet saves. */
@Component
public class SaveRateLimiter {

    private final int attempts;
    private final Duration duration;
    private final ConcurrentHashMap<String, SaveWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    @Autowired
    public SaveRateLimiter(AppProperties properties) {
        this(properties.accountSecurity().saveRateLimitAttempts(), properties.accountSecurity().saveRateLimitWindow());
    }

    SaveRateLimiter(int attempts, Duration duration) {
        this.attempts = Math.max(1, attempts);
        this.duration = duration.isNegative() || duration.isZero() ? Duration.ofSeconds(10) : duration;
    }

    public void check(String ownerId, String resource) {
        var now = Instant.now();
        var key = ownerId + '\n' + resource;
        var current = windows.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.startedAt().plus(duration).isAfter(now)) {
                return new SaveWindow(now, 1);
            }
            return new SaveWindow(previous.startedAt(), previous.attempts() + 1);
        });

        if ((requestCount.incrementAndGet() & 255) == 0) {
            windows.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(duration).isAfter(now));
        }
        if (current.attempts() > attempts) {
            var retryAt = current.startedAt().plus(duration);
            throw new TooManyRequestsException(
                    "保存操作过于频繁，请稍后重试",
                    retryAt.getEpochSecond() - now.getEpochSecond(),
                    "SAVE_RATE_LIMITED");
        }
    }

    private record SaveWindow(Instant startedAt, int attempts) {
    }
}
