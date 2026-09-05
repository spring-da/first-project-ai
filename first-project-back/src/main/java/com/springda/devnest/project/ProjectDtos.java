package com.springda.devnest.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> techStack,
            @NotNull ProjectStatus status,
            @Min(0) @Max(100) int progress,
            @NotBlank @Size(max = 500) String nextAction
    ) {
    }

    public record Response(
            String id,
            String name,
            String description,
            List<String> techStack,
            ProjectStatus status,
            int progress,
            String nextAction,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(ProjectEntity entity) {
            return new Response(
                    entity.getId(), entity.getName(), entity.getDescription(), entity.getTechStack(),
                    entity.getStatus(), entity.getProgress(), entity.getNextAction(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }
}
