package com.springda.devnest.admin;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "registration_invitations")
public class RegistrationInvitationEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "invited_by", length = 36)
    private String invitedBy;

    @Column(name = "registered_user_id", length = 36)
    private String registeredUserId;

    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected RegistrationInvitationEntity() {
    }

    public RegistrationInvitationEntity(String email, String invitedBy, String tokenHash, Instant expiresAt) {
        this.email = email;
        this.invitedBy = invitedBy;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void rotateToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void markRegistered(String userId) {
        this.registeredUserId = userId;
        this.tokenHash = null;
        this.expiresAt = null;
    }

    public String getEmail() {
        return email;
    }

    public String getRegisteredUserId() {
        return registeredUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isRegistered() {
        return registeredUserId != null;
    }
}
