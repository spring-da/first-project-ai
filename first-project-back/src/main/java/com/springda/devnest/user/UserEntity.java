package com.springda.devnest.user;

import com.springda.devnest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity implements UserDetails {

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "login_locked_until")
    private Instant loginLockedUntil;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    protected UserEntity() {
    }

    public UserEntity(String email, String passwordHash, String displayName) {
        this(email, passwordHash, displayName, UserRole.USER);
    }

    public UserEntity(String email, String passwordHash, String displayName, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public Instant getTemporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public boolean isLoginLocked(Instant now) {
        return loginLockedUntil != null && loginLockedUntil.isAfter(now);
    }

    public boolean isTemporaryPasswordExpired(Instant now) {
        return forcePasswordChange
                && temporaryPasswordExpiresAt != null
                && !temporaryPasswordExpiresAt.isAfter(now);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) {
            invalidateSessions();
        }
        this.enabled = enabled;
    }

    public void changePassword(String passwordHash, boolean forcePasswordChange) {
        this.passwordHash = passwordHash;
        this.forcePasswordChange = forcePasswordChange;
        this.temporaryPasswordExpiresAt = null;
        clearLoginFailures();
        invalidateSessions();
    }

    public void resetPassword(String passwordHash, Instant expiresAt) {
        this.passwordHash = passwordHash;
        this.forcePasswordChange = true;
        this.temporaryPasswordExpiresAt = expiresAt;
        clearLoginFailures();
        invalidateSessions();
    }

    public void requirePasswordChange() {
        this.forcePasswordChange = true;
        this.temporaryPasswordExpiresAt = null;
    }

    public void recordLoginFailure(Instant now, int threshold, Duration lockDuration) {
        if (loginLockedUntil != null && !loginLockedUntil.isAfter(now)) {
            failedLoginAttempts = 0;
            loginLockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= threshold) {
            loginLockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        loginLockedUntil = null;
    }

    private void invalidateSessions() {
        authVersion++;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
