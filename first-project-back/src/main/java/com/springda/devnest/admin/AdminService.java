package com.springda.devnest.admin;

import com.springda.devnest.common.ConflictException;
import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.auth.AccountCredentialService;
import com.springda.devnest.config.AppProperties;
import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.image.MarkdownImageRepository;
import com.springda.devnest.community.CommunityMessageRepository;
import com.springda.devnest.profile.ProfileRepository;
import com.springda.devnest.share.KnowledgeShareRepository;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.time.Instant;

@Service
public class AdminService {

    private final UserRepository users;
    private final RegistrationInvitationRepository invitations;
    private final PasswordEncoder passwordEncoder;
    private final MarkdownImageRepository images;
    private final CommunityMessageRepository communityMessages;
    private final KnowledgeShareRepository knowledgeShares;
    private final ProfileRepository profiles;
    private final ImageStorage imageStorage;
    private final AccountCredentialService credentials;
    private final AppProperties properties;
    private final AdminAuditService audit;

    public AdminService(
            UserRepository users,
            RegistrationInvitationRepository invitations,
            PasswordEncoder passwordEncoder,
            MarkdownImageRepository images,
            CommunityMessageRepository communityMessages,
            KnowledgeShareRepository knowledgeShares,
            ProfileRepository profiles,
            ImageStorage imageStorage,
            AccountCredentialService credentials,
            AppProperties properties,
            AdminAuditService audit
    ) {
        this.users = users;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.images = images;
        this.communityMessages = communityMessages;
        this.knowledgeShares = knowledgeShares;
        this.profiles = profiles;
        this.imageStorage = imageStorage;
        this.credentials = credentials;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public java.util.List<AdminDtos.AccountResponse> listAccounts(String adminId) {
        requireAdmin(adminId);
        var invitationByEmail = invitations.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        invitation -> normalizeEmail(invitation.getEmail()),
                        invitation -> invitation));
        var result = new ArrayList<AdminDtos.AccountResponse>();

        for (var user : users.findAll()) {
            var invitation = invitationByEmail.remove(normalizeEmail(user.getEmail()));
            result.add(registeredResponse(user, invitation));
        }
        for (var invitation : invitationByEmail.values()) {
            result.add(new AdminDtos.AccountResponse(
                    null,
                    invitation.getId(),
                    invitation.getEmail(),
                    null,
                    null,
                    false,
                    false,
                    false,
                    invitation.getCreatedAt(),
                    invitation.getExpiresAt(),
                    invitation.isExpired(Instant.now()),
                    null));
        }
        result.sort(Comparator.comparing(AdminDtos.AccountResponse::registered).reversed()
                .thenComparing(AdminDtos.AccountResponse::email, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional
    public AdminDtos.InvitationSecretResponse invite(String adminId, AdminDtos.InviteRequest request) {
        var admin = requireAdmin(adminId);
        var email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("该邮箱已经注册");
        }
        if (invitations.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("该邮箱已经在待注册名单中");
        }
        var token = credentials.newInvitationToken();
        var expiresAt = Instant.now().plus(properties.accountSecurity().invitationTokenTtl());
        var invitation = invitations.save(new RegistrationInvitationEntity(
                email, adminId, credentials.hashInvitationToken(token), expiresAt));
        var account = new AdminDtos.AccountResponse(
                null, invitation.getId(), invitation.getEmail(), null, null,
                false, false, false, invitation.getCreatedAt(), expiresAt, false, null);
        audit.record(adminId, admin.getEmail(), invitation.getId(), invitation.getEmail(),
                AdminAuditAction.INVITATION_CREATED, "invitations", "POST", "/api/v1/admin/invitations", 201, true);
        return new AdminDtos.InvitationSecretResponse(account, token, expiresAt);
    }

    @Transactional
    public AdminDtos.InvitationSecretResponse rotateInvitation(String adminId, String invitationId) {
        var admin = requireAdmin(adminId);
        var invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("注册邀请", invitationId));
        if (invitation.isRegistered()) {
            throw new ConflictException("该邮箱已经注册，请管理对应账号");
        }
        var token = credentials.newInvitationToken();
        var expiresAt = Instant.now().plus(properties.accountSecurity().invitationTokenTtl());
        invitation.rotateToken(credentials.hashInvitationToken(token), expiresAt);
        audit.record(adminId, admin.getEmail(), invitation.getId(), invitation.getEmail(),
                AdminAuditAction.INVITATION_ROTATED, "invitations", "POST",
                "/api/v1/admin/invitations/" + invitationId + "/rotate-token", 200, true);
        return new AdminDtos.InvitationSecretResponse(
                pendingResponse(invitation), token, expiresAt);
    }

    @Transactional
    public void revokeInvitation(String adminId, String invitationId) {
        var admin = requireAdmin(adminId);
        var invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("注册邀请", invitationId));
        if (invitation.isRegistered()) {
            throw new ConflictException("该邮箱已经注册，请管理对应账号");
        }
        invitations.delete(invitation);
        audit.record(adminId, admin.getEmail(), invitation.getId(), invitation.getEmail(),
                AdminAuditAction.INVITATION_REVOKED, "invitations", "DELETE",
                "/api/v1/admin/invitations/" + invitationId, 204, true);
    }

    @Transactional
    public AdminDtos.AccountResponse setEnabled(String adminId, String userId, boolean enabled) {
        var admin = requireAdmin(adminId);
        var user = manageableUser(userId);
        user.setEnabled(enabled);
        var invitation = invitations.findByEmailIgnoreCase(user.getEmail()).orElse(null);
        audit.record(adminId, admin.getEmail(), user.getId(), user.getEmail(),
                enabled ? AdminAuditAction.ACCOUNT_ENABLED : AdminAuditAction.ACCOUNT_DISABLED,
                "accounts", "PATCH", "/api/v1/admin/accounts/" + userId + "/status", 200, true);
        return registeredResponse(user, invitation);
    }

    @Transactional
    public AdminDtos.TemporaryPasswordResponse resetPassword(String adminId, String userId) {
        var admin = requireAdmin(adminId);
        var user = manageableUser(userId);
        var temporaryPassword = credentials.newTemporaryPassword();
        var expiresAt = Instant.now().plus(properties.accountSecurity().temporaryPasswordTtl());
        user.resetPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
        audit.record(adminId, admin.getEmail(), user.getId(), user.getEmail(),
                AdminAuditAction.ACCOUNT_PASSWORD_RESET, "accounts", "POST",
                "/api/v1/admin/accounts/" + userId + "/reset-password", 200, true);
        return new AdminDtos.TemporaryPasswordResponse(temporaryPassword, expiresAt);
    }

    @Transactional
    public void deleteAccount(String adminId, String userId) {
        var admin = requireAdmin(adminId);
        var user = manageableUser(userId);
        var targetEmail = user.getEmail();
        var objectKeys = new ArrayList<>(images.findAllByOwnerId(userId).stream()
                .map(image -> image.getObjectKey()).toList());
        objectKeys.addAll(communityMessages.findImageObjectKeysRemovedWithAuthor(userId));
        profiles.findByOwnerId(userId).map(profile -> profile.getAvatarObjectKey())
                .filter(java.util.Objects::nonNull).ifPresent(objectKeys::add);
        invitations.deleteByRegisteredUserId(userId);
        knowledgeShares.deleteAllByOwnerId(userId);
        users.delete(user);
        users.flush();
        audit.record(adminId, admin.getEmail(), userId, targetEmail,
                AdminAuditAction.ACCOUNT_DELETED, "accounts", "DELETE",
                "/api/v1/admin/accounts/" + userId, 204, true);

        if (!objectKeys.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    int failures = 0;
                    for (var objectKey : objectKeys) {
                        try {
                            imageStorage.delete(objectKey);
                        } catch (RuntimeException exception) {
                            failures++;
                        }
                    }
                    if (failures > 0) {
                        LoggerFactory.getLogger(AdminService.class)
                                .warn("Account deleted, but {} OSS image object(s) need manual cleanup.", failures);
                    }
                }
            });
        }
    }

    private UserEntity requireAdmin(String adminId) {
        var admin = users.findById(adminId)
                .orElseThrow(() -> new ForbiddenException("需要管理员权限"));
        if (!admin.isEnabled() || admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("需要管理员权限");
        }
        return admin;
    }

    private UserEntity manageableUser(String userId) {
        var user = users.findById(userId).orElseThrow(() -> new NotFoundException("账户", userId));
        if (user.getRole() == UserRole.ADMIN) {
            throw new ForbiddenException("管理员账户不能执行此操作");
        }
        return user;
    }

    private AdminDtos.AccountResponse registeredResponse(
            UserEntity user,
            RegistrationInvitationEntity invitation
    ) {
        return new AdminDtos.AccountResponse(
                user.getId(),
                invitation == null ? null : invitation.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                true,
                user.isEnabled(),
                user.isForcePasswordChange(),
                invitation == null ? null : invitation.getCreatedAt(),
                null,
                false,
                user.getCreatedAt());
    }

    private AdminDtos.AccountResponse pendingResponse(RegistrationInvitationEntity invitation) {
        return new AdminDtos.AccountResponse(
                null,
                invitation.getId(),
                invitation.getEmail(),
                null,
                null,
                false,
                false,
                false,
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.isExpired(Instant.now()),
                null);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
