package com.springda.devnest.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 240) String title,
            @Min(0) @Max(100000) int sortOrder,
            LocalDate scheduledDate,
            Instant dueAt,
            TaskPriority priority
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 240) String title,
            boolean done,
            @Min(0) @Max(100000) int sortOrder,
            LocalDate scheduledDate,
            Instant dueAt,
            @NotNull TaskPriority priority,
            boolean archived
    ) {
    }

    public record Response(
            String id,
            String title,
            boolean done,
            int sortOrder,
            LocalDate scheduledDate,
            Instant dueAt,
            TaskPriority priority,
            Instant completedAt,
            boolean archived,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(TaskEntity entity) {
            return new Response(
                    entity.getId(), entity.getTitle(), entity.isDone(), entity.getSortOrder(),
                    entity.getScheduledDate(), entity.getDueAt(), entity.getPriority(),
                    entity.getCompletedAt(), entity.isArchived(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }
}
