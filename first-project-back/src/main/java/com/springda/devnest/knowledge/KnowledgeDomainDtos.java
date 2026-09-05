package com.springda.devnest.knowledge;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class KnowledgeDomainDtos {

    private KnowledgeDomainDtos() {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 240) String description,
            @Min(0) int sortOrder
    ) {
    }

    public record Response(
            String id,
            String name,
            String description,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(KnowledgeDomainEntity entity) {
            return new Response(
                    entity.getId(), entity.getName(), entity.getDescription(), entity.getSortOrder(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }
}
