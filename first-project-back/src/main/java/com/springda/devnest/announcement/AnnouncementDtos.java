package com.springda.devnest.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AnnouncementDtos {
    private AnnouncementDtos() {
    }

    public record PublishRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 4000) String content
    ) {
    }

    public record Response(
            String id,
            String title,
            String content,
            String publisherName,
            boolean active,
            boolean read,
            long readCount,
            Instant publishedAt
    ) {
    }
}
