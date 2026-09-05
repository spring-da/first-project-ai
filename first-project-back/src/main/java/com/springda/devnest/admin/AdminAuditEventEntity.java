package com.springda.devnest.admin;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEventEntity extends BaseEntity {

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Column(name = "actor_email", nullable = false, length = 190)
    private String actorEmail;

    @Column(name = "target_id", length = 190)
    private String targetId;

    @Column(name = "target_label", length = 190)
    private String targetLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminAuditAction action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(nullable = false)
    private boolean success;

    protected AdminAuditEventEntity() {
    }

    public AdminAuditEventEntity(
            String actorId,
            String actorEmail,
            String targetId,
            String targetLabel,
            AdminAuditAction action,
            String resourceType,
            String httpMethod,
            String requestPath,
            int responseStatus,
            boolean success
    ) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.action = action;
        this.resourceType = resourceType;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.responseStatus = responseStatus;
        this.success = success;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public AdminAuditAction getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public boolean isSuccess() {
        return success;
    }
}
