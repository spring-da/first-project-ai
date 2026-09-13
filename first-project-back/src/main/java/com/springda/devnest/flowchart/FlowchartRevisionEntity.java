package com.springda.devnest.flowchart;

import com.springda.devnest.common.BaseEntity;

import jakarta.persistence.*;

@Entity
@Table(name = "flowchart_revisions")
public class FlowchartRevisionEntity extends BaseEntity {
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "document_version", nullable = false)
    private long documentVersion;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "domain_id", length = 36)
    private String domainId;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(nullable = false, columnDefinition = "text")
    private String diagram;

    protected FlowchartRevisionEntity() {}

    public FlowchartRevisionEntity(FlowchartEntity d, String action) {
        documentId = d.getId();
        ownerId = d.getOwnerId();
        documentVersion = d.getVersion();
        this.action = action;
        title = d.getTitle();
        domainId = d.getDomainId();
        favorite = d.isFavorite();
        diagram = d.getDiagram();
    }

    public long getDocumentVersion() {
        return documentVersion;
    }

    public String getAction() {
        return action;
    }

    public String getTitle() {
        return title;
    }

    public String getDomainId() {
        return domainId;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public String getDiagram() {
        return diagram;
    }

    public boolean matches(FlowchartEntity d) {
        return title.equals(d.getTitle())
                && java.util.Objects.equals(domainId, d.getDomainId())
                && favorite == d.isFavorite()
                && diagram.equals(d.getDiagram());
    }
}
