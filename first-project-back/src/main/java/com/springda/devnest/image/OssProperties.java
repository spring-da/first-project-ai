package com.springda.devnest.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.oss")
public record OssProperties(boolean enabled, String endpoint, String region, String bucket,
                            String accessKeyId, String accessKeySecret, String securityToken, String prefix,
                            ZoneId imageTimeZone, boolean publicRead) {
    @Override public String toString() { return "OssProperties[enabled=" + enabled + ", credentials=REDACTED]"; }
}
