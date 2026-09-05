package com.springda.devnest.task;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class TaskEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(name = "is_done", nullable = false)
    private boolean done;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    protected TaskEntity() {
    }

    public TaskEntity(
            String ownerId,
            String title,
            int sortOrder,
            LocalDate scheduledDate,
            Instant dueAt,
            TaskPriority priority
    ) {
        this.ownerId = ownerId;
        this.title = title;
        this.sortOrder = sortOrder;
        this.scheduledDate = scheduledDate;
        this.dueAt = dueAt;
        this.priority = priority;
    }

    public void update(
            String title,
            boolean done,
            int sortOrder,
            LocalDate scheduledDate,
            Instant dueAt,
            TaskPriority priority,
            boolean archived
    ) {
        this.title = title;
        if (done != this.done) this.completedAt = done ? Instant.now() : null;
        this.done = done;
        this.sortOrder = sortOrder;
        this.scheduledDate = scheduledDate;
        this.dueAt = dueAt;
        this.priority = priority;
        this.archived = archived;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDone() {
        return done;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isArchived() {
        return archived;
    }
}
