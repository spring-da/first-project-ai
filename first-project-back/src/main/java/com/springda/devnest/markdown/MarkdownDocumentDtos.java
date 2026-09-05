package com.springda.devnest.markdown;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MarkdownDocumentDtos {

    public static final int MAX_CONTENT_LENGTH = 1_000_000;
    public static final int MAX_IMPORT_DOCUMENTS = 50;
    public static final int MAX_EXPORT_DOCUMENTS = 100;
    public static final int SUMMARY_EXCERPT_LENGTH = 180;

    private MarkdownDocumentDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,
            @Size(max = 36) String domainId,
            boolean favorite
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,
            @Size(max = 36) String domainId,
            boolean favorite,
            @NotNull @PositiveOrZero Long expectedVersion
    ) {
    }

    public record RestoreRevisionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    public record RevisionSummary(String id, long documentVersion, String action, String title,
                                  String fileName, Instant createdAt) {
        static RevisionSummary from(MarkdownRevisionRepository.Summary revision) {
            return new RevisionSummary(revision.getId(), revision.getDocumentVersion(), revision.getAction(),
                    revision.getTitle(), revision.getFileName(), revision.getCreatedAt());
        }
    }

    public record RevisionResponse(String id, long documentVersion, String action, String title,
                                   String fileName, String content, String domainId, boolean favorite, Instant createdAt) {
        static RevisionResponse from(MarkdownRevisionEntity revision) {
            return new RevisionResponse(revision.getId(), revision.getDocumentVersion(), revision.getAction(),
                    revision.getTitle(), revision.getFileName(), revision.getContent(), revision.getDomainId(),
                    revision.isFavorite(), revision.getCreatedAt());
        }
    }

    public record ImportItem(
            @NotBlank @Size(max = 255) String fileName,
            @Size(max = 200) String title,
            @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,
            @Size(max = 36) String domainId,
            Boolean favorite
    ) {
    }

    public record ImportRequest(
            @NotNull @Size(min = 1, max = MAX_IMPORT_DOCUMENTS)
            List<@NotNull @Valid ImportItem> documents
    ) {
    }

    public record ExportRequest(
            @NotNull @Size(max = MAX_EXPORT_DOCUMENTS)
            List<@NotBlank @Size(max = 36) String> ids
    ) {
    }

    public record Response(
            String id,
            String title,
            String fileName,
            String content,
            boolean favorite,
            String domainId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Instant deletedAt
    ) {
        static Response from(MarkdownDocumentEntity entity) {
            return new Response(
                    entity.getId(), entity.getTitle(), entity.getFileName(), entity.getContent(),
                    entity.isFavorite(), entity.getDomainId(), entity.getCreatedAt(), entity.getUpdatedAt(),
                    entity.getVersion(), entity.getDeletedAt());
        }
    }

    public record SummaryResponse(
            String id,
            String title,
            String fileName,
            String excerpt,
            int contentLength,
            boolean favorite,
            String domainId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Instant deletedAt
    ) {
        static SummaryResponse from(MarkdownDocumentRepository.SummaryProjection projection) {
            var contentLength = projection.getContentLength() == null ? 0 : projection.getContentLength();
            var excerpt = projection.getExcerpt() == null ? "" : projection.getExcerpt();
            if (contentLength > SUMMARY_EXCERPT_LENGTH) excerpt += "…";
            return new SummaryResponse(
                    projection.getId(), projection.getTitle(), projection.getFileName(), excerpt,
                    contentLength, Boolean.TRUE.equals(projection.getFavorite()), projection.getDomainId(),
                    projection.getCreatedAt(), projection.getUpdatedAt(), projection.getVersion(), projection.getDeletedAt());
        }
    }

    public record ImportResponse(
            int importedCount,
            List<Response> documents,
            List<ImportFailure> errors
    ) {
        public ImportResponse(int importedCount, List<Response> documents) {
            this(importedCount, documents, List.of());
        }
    }

    public record ImportFailure(String fileName, String message) {}

    public record BulkDomainItem(
            @NotBlank @Size(max = 36) String id,
            @NotNull @PositiveOrZero Long expectedVersion
    ) {}

    public record BulkDomainRequest(
            @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid BulkDomainItem> documents,
            @Size(max = 36) String domainId
    ) {}
}
