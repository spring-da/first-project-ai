package com.springda.devnest.project;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
public class ProjectEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_tech_stack", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "tech_name", nullable = false, length = 80)
    private List<String> techStack = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "next_action", nullable = false, length = 500)
    private String nextAction;

    protected ProjectEntity() {
    }

    public ProjectEntity(
            String ownerId,
            String name,
            String description,
            List<String> techStack,
            ProjectStatus status,
            int progress,
            String nextAction
    ) {
        this.ownerId = ownerId;
        update(name, description, techStack, status, progress, nextAction);
    }

    public void update(
            String name,
            String description,
            List<String> techStack,
            ProjectStatus status,
            int progress,
            String nextAction
    ) {
        this.name = name;
        this.description = description;
        this.techStack.clear();
        this.techStack.addAll(techStack);
        this.status = status;
        this.progress = progress;
        this.nextAction = nextAction;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getTechStack() { return List.copyOf(techStack); }
    public ProjectStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getNextAction() { return nextAction; }
}
