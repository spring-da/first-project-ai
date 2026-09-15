package com.springda.devnest.sharing;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareBundleTokenProtectorTest {
    private final ShareBundleTokenProtector protector = new ShareBundleTokenProtector(
            new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

    @Test void roundTripsWithoutStoringThePlainToken() {
        String token = "a".repeat(43);
        String protectedToken = protector.protect(token);
        assertThat(protectedToken).startsWith("v1.").doesNotContain(token);
        assertThat(protector.reveal(protectedToken)).isEqualTo(token);
        assertThat(protector.protect(token)).isNotEqualTo(protectedToken);
    }

    @Test void rejectsTamperedCiphertext() {
        String protectedToken = protector.protect("b".repeat(43));
        String tampered = protectedToken.substring(0, protectedToken.length() - 1)
                + (protectedToken.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> protector.reveal(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
