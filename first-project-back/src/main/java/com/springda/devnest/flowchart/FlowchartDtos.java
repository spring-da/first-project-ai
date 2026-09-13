package com.springda.devnest.flowchart;

import jakarta.validation.constraints.*;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public final class FlowchartDtos {
    private FlowchartDtos() {}

    public enum SaveMode {
        AUTO,
        MANUAL
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 36) String domainId,
            @NotNull Boolean favorite,
            @NotNull JsonNode diagram,
            @NotNull UUID creationKey) {
        public CreateRequest {
            if (title != null) title = title.strip();
        }
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 36) String domainId,
            @NotNull Boolean favorite,
            @NotNull JsonNode diagram,
            @NotNull @Min(0) Long expectedVersion,
            SaveMode saveMode) {
        public UpdateRequest {
            if (title != null) title = title.strip();
        }
    }

    public record RestoreRequest(@NotNull @Min(0) Long expectedVersion) {}

    public record Summary(
            String id,
            String title,
            String domainId,
            boolean favorite,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Instant deletedAt,
            int nodeCount,
            int edgeCount,
            String excerpt) {}

    public record Response(
            String id,
            String title,
            String domainId,
            boolean favorite,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Instant deletedAt,
            int nodeCount,
            int edgeCount,
            String excerpt,
            JsonNode diagram) {}

    public record RevisionSummary(
            String id, long documentVersion, String title, String action, Instant createdAt) {}

    public record RevisionResponse(
            String id,
            long documentVersion,
            String title,
            String action,
            Instant createdAt,
            String domainId,
            boolean favorite,
            JsonNode diagram) {}
}
