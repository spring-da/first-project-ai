package com.springda.devnest.admin;

import com.springda.devnest.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record InviteRequest(
            @NotBlank @Email @Size(max = 190) String email
    ) {
    }

    public record StatusRequest(boolean enabled) {
    }

    public record AccountResponse(
            String userId,
            String invitationId,
            String email,
            String displayName,
            UserRole role,
            boolean registered,
            boolean enabled,
            boolean mustChangePassword,
            Instant invitedAt,
            Instant invitationExpiresAt,
            boolean invitationExpired,
            Instant registeredAt
    ) {
    }

    public record InvitationSecretResponse(
            AccountResponse account,
            String invitationToken,
            Instant expiresAt
    ) {
    }

    public record TemporaryPasswordResponse(
            String temporaryPassword,
            Instant expiresAt
    ) {
    }
}
