package com.springda.devnest.sharing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class SharingDtos {
    private SharingDtos() {}
    public record Reference(@NotNull ResourceType type, @NotBlank @Size(max = 36) String id) {}
    public record PublishRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Reference> items) {}
    public record RevokeRequest(@NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 36) String> ids) {}
    public record CreateLinkRequest(@NotBlank @Size(max = 200) String title,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Reference> items, @NotNull @Future Instant expiresAt) {}
    public record PublishResult(int publishedCount, int existingCount) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record PoolSummary(String id, ResourceType resourceType, String title, String excerpt,
            String authorName, Instant sharedAt, Instant updatedAt, boolean mine) {}
    public record SharedContent(String id, ResourceType resourceType, String title, String content,
            String language, String category, List<String> tags, Instant updatedAt, tools.jackson.databind.JsonNode diagram) {}
    public record LinkSecret(String id, String token, String title, Instant expiresAt, Instant createdAt) {}
    public record LinkSummary(String id, String title, Instant expiresAt, Instant createdAt,
            Instant revokedAt, boolean active, int itemCount) {}
    public record ManagedItem(String id, ResourceType resourceType, String resourceId, String title,
            Instant removedAt, boolean available) {}
    public record LinkDetail(LinkSummary link, List<ManagedItem> items) {}
    public record PublicItem(String id, ResourceType resourceType, String title) {}
    public record PublicBundle(String title, Instant expiresAt, List<PublicItem> items) {}
}
