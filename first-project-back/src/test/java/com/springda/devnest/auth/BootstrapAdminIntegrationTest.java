package com.springda.devnest.auth;

import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.profile.ProfileRepository;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItYm9vdHN0cmFwLWFkbWluLXRlc3Q=",
        "app.security.issuer=devnest-api",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false",
        "app.account-security.bootstrap-admin-email=owner@example.com",
        "app.account-security.bootstrap-admin-password=correct-horse-battery-staple"
})
class BootstrapAdminIntegrationTest {

    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean ImageStorage imageStorage;

    @Test
    void createsExactlyOneForcedChangeAdministratorForAnEmptyDatabase() {
        assertThat(users.count()).isOne();
        var admin = users.findByEmailIgnoreCase("owner@example.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isForcePasswordChange()).isTrue();
        assertThat(passwordEncoder.matches("correct-horse-battery-staple", admin.getPassword())).isTrue();
        assertThat(profiles.findByOwnerId(admin.getId())).isPresent();
    }
}
