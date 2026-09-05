package com.springda.devnest.auth;

import com.springda.devnest.config.AppProperties;
import com.springda.devnest.profile.ProfileEntity;
import com.springda.devnest.profile.ProfileRepository;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public BootstrapAdminInitializer(
            UserRepository users,
            ProfileRepository profiles,
            PasswordEncoder passwordEncoder,
            AppProperties properties
    ) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        var emailValue = properties.accountSecurity().bootstrapAdminEmail();
        var password = properties.accountSecurity().bootstrapAdminPassword();
        var email = emailValue == null ? "" : emailValue.trim().toLowerCase(Locale.ROOT);
        var passwordValue = password == null ? "" : password;

        if (users.count() != 0) {
            if (!email.isEmpty() || !passwordValue.isEmpty()) {
                LoggerFactory.getLogger(BootstrapAdminInitializer.class).warn(
                        "Bootstrap administrator configuration was ignored because accounts already exist. Remove the bootstrap environment variables.");
            }
            return;
        }
        if (email.isEmpty() && passwordValue.isEmpty()) {
            if (properties.accountSecurity().bootstrapAdminRequired()) {
                throw new IllegalStateException(
                        "数据库中没有账号。首次启动必须配置 BOOTSTRAP_ADMIN_EMAIL 和 BOOTSTRAP_ADMIN_PASSWORD");
            }
            return;
        }
        if (email.isEmpty() || passwordValue.isEmpty()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL 和 BOOTSTRAP_ADMIN_PASSWORD 必须同时配置");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL 格式不正确");
        }
        if (passwordValue.length() < 16 || passwordValue.length() > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD 必须为 16 到 72 个字符");
        }
        var admin = new UserEntity(
                email,
                passwordEncoder.encode(passwordValue),
                "Administrator",
                UserRole.ADMIN);
        admin.requirePasswordChange();
        users.save(admin);
        profiles.save(ProfileEntity.initial(admin.getId(), admin.getDisplayName()));
        LoggerFactory.getLogger(BootstrapAdminInitializer.class).info(
                "Bootstrap administrator created. Remove the bootstrap environment variables and change the password at first login.");
    }
}
