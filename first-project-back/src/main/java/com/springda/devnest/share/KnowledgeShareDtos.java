package com.springda.devnest.share;


import com.springda.devnest.snippet.SnippetEntity;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class KnowledgeShareDtos {
    private KnowledgeShareDtos() {
    }

    public record CreateRequest(@NotNull @Future Instant expiresAt) {
    }

    public record SecretResponse(String id, String token, Instant expiresAt, Instant createdAt) {
        static SecretResponse from(KnowledgeShareEntity share, String token) {
            return new SecretResponse(share.getId(), token, share.getExpiresAt(), share.getCreatedAt());
        }
    }

    public record SummaryResponse(
            String id,
            Instant expiresAt,
            Instant createdAt,
            Instant revokedAt,
            boolean active
    ) {
        static SummaryResponse from(KnowledgeShareEntity share, Instant now) {
            return new SummaryResponse(
                    share.getId(), share.getExpiresAt(), share.getCreatedAt(), share.getRevokedAt(), share.isActive(now));
        }
    }

    public record PublicResponse(
            KnowledgeResourceType resourceType,
            String title,
            String content,
            String language,
            String category,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            tools.jackson.databind.JsonNode diagram
    ) {
        static PublicResponse from(SnippetEntity snippet, KnowledgeShareEntity share) {
            return new PublicResponse(
                    KnowledgeResourceType.SNIPPET, snippet.getTitle(), snippet.getCode(), snippet.getLanguage(),
                    null, List.of(), snippet.getCreatedAt(), snippet.getUpdatedAt(), share.getExpiresAt(), null);
        }

    }
}
