package com.springda.devnest.auth;

import com.springda.devnest.config.AppProperties;
import com.springda.devnest.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LoginProtectionService {

    private final UserRepository users;
    private final AppProperties properties;

    public LoginProtectionService(UserRepository users, AppProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public void recordFailure(String email) {
        users.findByEmailIgnoreCaseForUpdate(email).ifPresent(user -> {
            if (user.isEnabled()) {
                user.recordLoginFailure(
                        Instant.now(),
                        properties.accountSecurity().loginFailureThreshold(),
                        properties.accountSecurity().loginLockDuration());
            }
        });
    }

    @Transactional
    public void recordSuccess(String email) {
        users.findByEmailIgnoreCaseForUpdate(email).ifPresent(user -> {
            if (user.isEnabled()) {
                user.clearLoginFailures();
            }
        });
    }
}
