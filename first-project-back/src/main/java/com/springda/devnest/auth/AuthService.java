package com.springda.devnest.auth;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.admin.RegistrationInvitationRepository;
import com.springda.devnest.profile.ProfileEntity;
import com.springda.devnest.profile.ProfileRepository;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RegistrationInvitationRepository invitations;
    private final AccountCredentialService credentials;
    private final LoginRateLimiter rateLimiter;
    private final LoginProtectionService loginProtection;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository users,
            ProfileRepository profiles,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            RegistrationInvitationRepository invitations,
            AccountCredentialService credentials,
            LoginRateLimiter rateLimiter,
            LoginProtectionService loginProtection
    ) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.invitations = invitations;
        this.credentials = credentials;
        this.rateLimiter = rateLimiter;
        this.loginProtection = loginProtection;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        var tokenHash = credentials.hashInvitationToken(request.invitationToken().trim());
        var invitation = invitations.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidInvitation);
        if (invitation.isRegistered() || invitation.isExpired(Instant.now())) {
            throw invalidInvitation();
        }

        var email = normalizeEmail(invitation.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw invalidInvitation();
        }

        var user = users.save(new UserEntity(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        profiles.save(ProfileEntity.initial(user.getId(), request.displayName().trim()));
        invitation.markRegistered(user.getId());
        return response(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String remoteAddress) {
        var email = normalizeEmail(request.email());
        rateLimiter.check(email, remoteAddress);

        var user = users.findByEmailIgnoreCase(email).orElse(null);
        var passwordMatches = passwordEncoder.matches(
                request.password(), user == null ? dummyPasswordHash : user.getPassword());
        var now = Instant.now();
        if (user == null || !passwordMatches || !user.isEnabled()
                || user.isLoginLocked(now) || user.isTemporaryPasswordExpired(now)) {
            if (user != null && !passwordMatches && !user.isLoginLocked(now)) {
                loginProtection.recordFailure(email);
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        loginProtection.recordSuccess(email);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserSummary currentUser(String userId) {
        return summary(users.findById(userId).orElseThrow(() -> new NotFoundException("账户", userId)));
    }

    @Transactional
    public AuthDtos.AuthResponse changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        var user = users.findById(userId).orElseThrow(() -> new NotFoundException("账户", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("新密码不能与当前密码相同");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), false);
        return response(user);
    }

    @Transactional
    public void logoutAll(String userId) {
        var user = users.findById(userId).orElseThrow(() -> new NotFoundException("账户", userId));
        user.revokeSessions();
    }

    private AuthDtos.AuthResponse response(UserEntity user) {
        return new AuthDtos.AuthResponse(
                tokens.createAccessToken(user),
                "Bearer",
                tokens.accessTokenTtlSeconds(),
                summary(user));
    }

    private AuthDtos.UserSummary summary(UserEntity user) {
        return new AuthDtos.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isForcePasswordChange());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private BadRequestException invalidInvitation() {
        return new BadRequestException("邀请链接无效、已使用或已过期，请联系管理员重新获取");
    }
}
