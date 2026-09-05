package com.springda.devnest.knowledge;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_domains")
public class KnowledgeDomainEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected KnowledgeDomainEntity() {
    }

    public KnowledgeDomainEntity(String ownerId, String name, String description, int sortOrder) {
        this.ownerId = ownerId;
        update(name, description, sortOrder);
    }

    public void update(String name, String description, int sortOrder) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
