package com.springda.devnest.log;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DevLogDtos {

    private DevLogDtos() {
    }

    public record SaveRequest(
            @Size(max = 240) String title,
            @NotBlank @Size(max = 200000) String content,
            @NotNull LogCategory category,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 80) String> tags,
            boolean pinned,
            @Size(max = 36) String domainId
    ) {
    }

    public record Response(
            String id,
            String title,
            String content,
            LogCategory category,
            List<String> tags,
            boolean pinned,
            String domainId,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(DevLogEntity entity) {
            return new Response(
                    entity.getId(), entity.getTitle(), entity.getContent(), entity.getCategory(), entity.getTags(),
                    entity.isPinned(), entity.getDomainId(), entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }

    public record TrashResponse(
            String id,
            String title,
            LogCategory category,
            boolean pinned,
            String domainId,
            String excerpt,
            Instant deletedAt
    ) {
        static TrashResponse from(DevLogEntity entity) {
            return new TrashResponse(
                    entity.getId(), entity.getTitle(), entity.getCategory(), entity.isPinned(),
                    entity.getDomainId(), firstLine(entity.getContent(), 140), entity.getDeletedAt());
        }
    }

    private static String firstLine(String value, int maxLength) {
        var line = value.lines().filter(candidate -> !candidate.isBlank()).findFirst().orElse("").trim();
        return line.length() <= maxLength ? line : line.substring(0, maxLength) + "…";
    }
}
