package com.springda.devnest.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaveRateLimiterTest {

    @Test
    void limitsEachAccountAndResourceIndependently() {
        var limiter = new SaveRateLimiter(2, Duration.ofMinutes(1));

        limiter.check("user-a", "snippets");
        limiter.check("user-a", "snippets");
        limiter.check("user-a", "markdown-documents");
        limiter.check("user-b", "snippets");

        assertThatThrownBy(() -> limiter.check("user-a", "snippets"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("保存操作过于频繁");
    }
}
