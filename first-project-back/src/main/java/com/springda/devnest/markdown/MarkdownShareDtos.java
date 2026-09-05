package com.springda.devnest.markdown;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class MarkdownShareDtos {
    private MarkdownShareDtos() {
    }

    public record CreateRequest(@NotNull @Future Instant expiresAt) {
    }

    public record SecretResponse(String id, String token, Instant expiresAt, Instant createdAt) {
        static SecretResponse from(MarkdownShareEntity share, String token) {
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
        static SummaryResponse from(MarkdownShareEntity share, Instant now) {
            return new SummaryResponse(
                    share.getId(), share.getExpiresAt(), share.getCreatedAt(), share.getRevokedAt(), share.isActive(now));
        }
    }

    public record PublicDocumentResponse(
            String title,
            String fileName,
            String content,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt
    ) {
        static PublicDocumentResponse from(MarkdownDocumentEntity document, MarkdownShareEntity share) {
            return new PublicDocumentResponse(
                    document.getTitle(), document.getFileName(), document.getContent(),
                    document.getCreatedAt(), document.getUpdatedAt(), share.getExpiresAt());
        }
    }
}
