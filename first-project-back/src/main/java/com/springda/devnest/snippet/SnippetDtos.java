package com.springda.devnest.snippet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SnippetDtos {

    private SnippetDtos() {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 80) String language,
            @NotBlank @Size(max = 200000) String code,
            boolean favorite,
            @Size(max = 36) String domainId
    ) {
    }

    public record Response(
            String id,
            String title,
            String language,
            String code,
            boolean favorite,
            String domainId,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(SnippetEntity entity) {
            return new Response(
                    entity.getId(), entity.getTitle(), entity.getLanguage(), entity.getCode(),
                    entity.isFavorite(), entity.getDomainId(), entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }

    public record TrashResponse(
            String id,
            String title,
            String language,
            boolean favorite,
            String domainId,
            String excerpt,
            Instant deletedAt
    ) {
        static TrashResponse from(SnippetEntity entity) {
            return new TrashResponse(
                    entity.getId(), entity.getTitle(), entity.getLanguage(), entity.isFavorite(),
                    entity.getDomainId(), firstLine(entity.getCode(), 140), entity.getDeletedAt());
        }
    }

    private static String firstLine(String value, int maxLength) {
        var line = value.lines().filter(candidate -> !candidate.isBlank()).findFirst().orElse("").trim();
        return line.length() <= maxLength ? line : line.substring(0, maxLength) + "…";
    }
}
