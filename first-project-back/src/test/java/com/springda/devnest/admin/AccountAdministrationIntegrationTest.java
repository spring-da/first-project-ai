package com.springda.devnest.admin;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.image.ImageStorage;
import com.springda.devnest.profile.ProfileRepository;
import com.springda.devnest.profile.ProfileEntity;
import com.springda.devnest.task.TaskEntity;
import com.springda.devnest.task.TaskRepository;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-administration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItYWNjb3VudC1hZG1pbi10ZXN0cw==",
        "app.security.issuer=devnest-api",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false"
})
@Transactional
class AccountAdministrationIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RegistrationInvitationRepository invitations;
    @Autowired ProfileRepository profiles;
    @Autowired TaskRepository tasks;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminAuditEventRepository auditEvents;
    @Autowired com.springda.devnest.image.MarkdownImageRepository images;
    @MockitoBean ImageStorage imageStorage;
    private MockMvc mvc;
    private UserEntity admin;
    private UserEntity user;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.save(new UserEntity(
                "springda0099@gmail.com", passwordEncoder.encode("admin-password"), "Administrator", UserRole.ADMIN));
        user = users.save(new UserEntity(
                "friend@example.com", passwordEncoder.encode("friend-password"), "Friend"));
        profiles.save(ProfileEntity.initial(admin.getId(), admin.getDisplayName()));
        profiles.save(ProfileEntity.initial(user.getId(), user.getDisplayName()));
    }

    @Test
    void onlyAdministratorsCanReadOrChangeAccountDirectory() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/accounts").with(appJwt(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts").with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registered").value(true));
    }

    @Test
    void administratorCanReadOneUsersWorkspaceWithoutImpersonatingThem() throws Exception {
        tasks.save(new TaskEntity(user.getId(), "Private user task", 0, null, null,
                com.springda.devnest.task.TaskPriority.NORMAL));

        mvc.perform(get("/api/v1/admin/accounts/{id}/workspace", user.getId()).with(appJwt(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts/{id}/workspace", user.getId()).with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("friend@example.com"))
                .andExpect(jsonPath("$.tasks[0].title").value("Private user task"))
                .andExpect(jsonPath("$.markdownDocuments").isArray());
    }

    @Test
    void registrationRequiresAValidSingleUseInvitationToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","password":"strong-password","displayName":"Stranger"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("邀请链接无效、已使用或已过期，请联系管理员重新获取"));

        var invitationResult = mvc.perform(post("/api/v1/admin/invitations").with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Invited@Example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.account.email").value("invited@example.com"))
                .andExpect(jsonPath("$.account.registered").value(false))
                .andExpect(jsonPath("$.invitationToken").isNotEmpty())
                .andReturn();
        String invitationToken = JsonPath.read(
                invitationResult.getResponse().getContentAsString(), "$.invitationToken");

        var directoryResult = mvc.perform(get("/api/v1/admin/accounts").with(appJwt(admin)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(directoryResult.getResponse().getContentAsString()).doesNotContain(invitationToken);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"%s","password":"strong-password","displayName":"Invited User"}
                                """.formatted(invitationToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));

        var registered = users.findByEmailIgnoreCase("invited@example.com").orElseThrow();
        assertThat(profiles.findByOwnerId(registered.getId())).isPresent();
        assertThat(invitations.findByEmailIgnoreCase("invited@example.com").orElseThrow().getRegisteredUserId())
                .isEqualTo(registered.getId());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"%s","password":"another-password","displayName":"Again"}
                                """.formatted(invitationToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("邀请链接无效、已使用或已过期，请联系管理员重新获取"));
    }

    @Test
    void rotatingAnInvitationImmediatelyInvalidatesThePreviousToken() throws Exception {
        var createdResult = mvc.perform(post("/api/v1/admin/invitations").with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rotate@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String oldToken = JsonPath.read(createdResult.getResponse().getContentAsString(), "$.invitationToken");
        String invitationId = JsonPath.read(createdResult.getResponse().getContentAsString(), "$.account.invitationId");

        var rotatedResult = mvc.perform(post("/api/v1/admin/invitations/{id}/rotate-token", invitationId)
                        .with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String newToken = JsonPath.read(rotatedResult.getResponse().getContentAsString(), "$.invitationToken");
        assertThat(newToken).isNotEqualTo(oldToken);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"%s","password":"strong-password","displayName":"Old Link"}
                                """.formatted(oldToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"%s","password":"strong-password","displayName":"New Link"}
                                """.formatted(newToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("rotate@example.com"));
    }

    @Test
    void repeatedPasswordFailuresTemporarilyLockTheAccount() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"friend@example.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("邮箱或密码错误"));
        }

        assertThat(users.findById(user.getId()).orElseThrow().isLoginLocked(Instant.now())).isTrue();
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@example.com\",\"password\":\"friend-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("邮箱或密码错误"));
    }

    @Test
    void registrationAndProfileUpdatesRejectAnOccupiedNickname() throws Exception {
        var invitationResult = mvc.perform(post("/api/v1/admin/invitations").with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nickname@example.com\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = JsonPath.read(invitationResult.getResponse().getContentAsString(), "$.invitationToken");

        mvc.perform(get("/api/v1/auth/display-name-availability").param("displayName", " friend "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationToken":"%s","password":"strong-password","displayName":" FRIEND "}
                                """.formatted(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("该昵称已被占用，请换一个昵称"));

        mvc.perform(put("/api/v1/profile").with(appJwt(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"administrator\",\"role\":\"Developer\",\"bio\":\"Hello\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("该昵称已被占用，请换一个昵称"));
        assertThat(users.findById(user.getId()).orElseThrow().getDisplayName()).isEqualTo("Friend");
    }

    @Test
    void memberCanUploadReadAndResetAnAvatarAndLeaveGenderUnset() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");
        mvc.perform(multipart("/api/v1/profile/avatar").file(
                                new MockMultipartFile("file", "avatar.png", "image/png", png))
                        .with(appJwt(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/user-avatars/" + user.getId()))
                .andExpect(jsonPath("$.gender").doesNotExist());

        var profile = profiles.findByOwnerId(user.getId()).orElseThrow();
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(png);
            return null;
        }).when(imageStorage).transferTo(eq(profile.getAvatarObjectKey()), any(java.io.OutputStream.class));
        var imageRequest = mvc.perform(get("/api/v1/user-avatars/{id}", user.getId()).with(appJwt(admin)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(imageRequest))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));

        mvc.perform(put("/api/v1/profile").with(appJwt(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Friend\",\"role\":\"Developer\",\"bio\":\"Hello\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.avatarUrl").value("/user-avatars/" + user.getId()));

        mvc.perform(delete("/api/v1/profile/avatar").with(appJwt(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void userCanRevokeEveryIssuedSessionImmediately() throws Exception {
        var existingSession = appJwt(user);

        mvc.perform(post("/api/v1/auth/logout-all").with(existingSession))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(get("/api/v1/tasks").with(existingSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void authenticationAndAccountDirectoryResponsesCannotBeCached() throws Exception {
        mvc.perform(get("/api/v1/auth/me").with(appJwt(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/admin/accounts").with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void administratorAccountAndDelegatedWritesAppearInTheAuditTrail() throws Exception {
        mvc.perform(post("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Audited task\",\"sortOrder\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/admin/accounts/{id}/status", user.getId()).with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/audit-events?limit=20").with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].action").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$[0].targetLabel").value("friend@example.com"))
                .andExpect(jsonPath("$[1].action").value("MEMBER_WORKSPACE_WRITE"))
                .andExpect(jsonPath("$[1].resourceType").value("tasks"))
                .andExpect(jsonPath("$[1].httpMethod").value("POST"))
                .andExpect(jsonPath("$[1].success").value(true));
        assertThat(auditEvents.count()).isEqualTo(2);
    }

    @Test
    void profileRejectsUnsafeAvatarSchemesEvenWhenTheApiIsCalledDirectly() throws Exception {
        mvc.perform(put("/api/v1/profile").with(appJwt(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Friend\",\"role\":\"Developer\",\"bio\":\"Hello\",\"avatarUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("头像链接必须是有效的 HTTP 或 HTTPS 地址"));
    }

    @Test
    void loginBurstIsRateLimitedWithoutDisclosingAccountState() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"burst@example.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"burst@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void disablingAnAccountInvalidatesItsExistingTokenImmediately() throws Exception {
        mvc.perform(get("/api/v1/tasks").with(appJwt(user)))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/accounts/{id}/status", user.getId()).with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/tasks").with(appJwt(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_UNAVAILABLE"));
    }

    @Test
    void resetPasswordReturnsOneTimeRandomCredentialAndRevokesOldTokens() throws Exception {
        var oldToken = appJwt(user);
        var resetResult = mvc.perform(post("/api/v1/admin/accounts/{id}/reset-password", user.getId())
                        .with(appJwt(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        String temporaryPassword = JsonPath.read(
                resetResult.getResponse().getContentAsString(), "$.temporaryPassword");

        var resetUser = users.findById(user.getId()).orElseThrow();
        assertThat(resetUser.isForcePasswordChange()).isTrue();
        assertThat(temporaryPassword).hasSize(20).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches(temporaryPassword, resetUser.getPassword())).isTrue();
        assertThat(resetUser.getTemporaryPasswordExpiresAt()).isAfter(Instant.now());

        mvc.perform(get("/api/v1/tasks").with(oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"friend@example.com","password":"%s"}
                                """.formatted(temporaryPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String temporaryToken = JsonPath.read(
                loginResult.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        var changeResult = mvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + temporaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"new-secure-password"}
                                """.formatted(temporaryPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        String changedToken = JsonPath.read(
                changeResult.getResponse().getContentAsString(), "$.accessToken");

        assertThat(passwordEncoder.matches(
                "new-secure-password", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
        mvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + changedToken))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTemporaryPasswordCannotLoginOrUseARecoveryToken() throws Exception {
        user.resetPassword(passwordEncoder.encode("expired-temporary-password"), Instant.now().minusSeconds(1));
        users.flush();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@example.com\",\"password\":\"expired-temporary-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("邮箱或密码错误"));
        mvc.perform(get("/api/v1/auth/me").with(appJwt(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
    }

    @Test
    void administratorCannotBeDisabledResetOrDeleted() throws Exception {
        mvc.perform(patch("/api/v1/admin/accounts/{id}/status", admin.getId()).with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/accounts/{id}/reset-password", admin.getId()).with(appJwt(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/accounts/{id}", admin.getId()).with(appJwt(admin)))
                .andExpect(status().isForbidden());
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void deletingAUserRemovesItsRegistrationAccessAndInvalidatesItsToken() throws Exception {
        var invitation = invitations.save(new RegistrationInvitationEntity(
                user.getEmail(), admin.getId(), "a".repeat(64), Instant.now().plusSeconds(3600)));
        invitation.markRegistered(user.getId());

        mvc.perform(delete("/api/v1/admin/accounts/{id}", user.getId()).with(appJwt(admin)))
                .andExpect(status().isNoContent());

        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(invitations.findByEmailIgnoreCase(user.getEmail())).isEmpty();
        mvc.perform(get("/api/v1/tasks").with(appJwt(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanCreateUpdateAndDeleteEveryMembersBusinessModule() throws Exception {
        var payloads = java.util.Map.of(
                "tasks", "{\"title\":\"Original\",\"sortOrder\":0,\"priority\":\"HIGH\"}",
                "projects", "{\"name\":\"Original\",\"techStack\":[\"Vue\"],\"status\":\"BUILDING\",\"progress\":15,\"nextAction\":\"Continue\"}",
                "domains", "{\"name\":\"Original\",\"description\":\"Test\",\"sortOrder\":0}",
                "snippets", "{\"title\":\"Original\",\"language\":\"sql\",\"code\":\"select 1\",\"favorite\":false}",
                "flowcharts", "{\"title\":\"Original\",\"domainId\":null,\"favorite\":false,\"diagram\":{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]},\"creationKey\":\"11111111-1111-4111-8111-111111111111\"}");
        for (var entry : payloads.entrySet()) {
            var path = "/api/v1/" + entry.getKey();
            var updateBody = entry.getValue();
            if (entry.getKey().equals("flowcharts")) updateBody = updateBody.substring(0, updateBody.length()-1) + ",\"expectedVersion\":0}";
            var created = mvc.perform(post(path).with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(entry.getValue()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String id = JsonPath.read(created, "$.id");
            var memberList = mvc.perform(get(path).with(appJwt(user))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(memberList).contains(id);
            var adminList = mvc.perform(get(path).with(appJwt(admin))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(adminList).doesNotContain(id);
            mvc.perform(put(path + "/" + id).with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                            .contentType(MediaType.APPLICATION_JSON).content((entry.getKey().equals("tasks") ? entry.getValue().replace("}", ",\"done\":false,\"archived\":false}") : updateBody).replace("Original", "Updated")))
                    .andExpect(status().isOk());
            assertThat(mvc.perform(get(path).with(appJwt(user))).andReturn().getResponse().getContentAsString())
                    .contains("Updated").doesNotContain("Original");
            mvc.perform(delete(path + "/" + id).with(appJwt(admin))).andExpect(status().isNotFound());
            mvc.perform(delete(path + "/" + id).with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                    .andExpect(status().isNoContent());
            if (entry.getKey().equals("snippets") || entry.getKey().equals("flowcharts")) {
                mvc.perform(get(path + "/trash").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                        .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
                mvc.perform(post(path + "/" + id + "/restore").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                        .andExpect(status().isOk());
            }
        }
        mvc.perform(put("/api/v1/profile").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Member updated\",\"role\":\"Developer\",\"bio\":\"New bio\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile").with(appJwt(user))).andExpect(jsonPath("$.name").value("Member updated"));
        mvc.perform(get("/api/v1/profile").with(appJwt(admin))).andExpect(jsonPath("$.name").value("Administrator"));
        mvc.perform(get("/api/v1/auth/me").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(jsonPath("$.id").value(admin.getId())).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void memberHeaderCannotEscalatePermissionsAndInvalidTargetsFailClosed() throws Exception {
        for (var path : java.util.List.of("tasks", "projects", "domains", "snippets", "flowcharts", "profile", "markdown-documents")) {
            mvc.perform(get("/api/v1/" + path).header("X-Workspace-Owner", user.getId()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/" + path).with(appJwt(user)).header("X-Workspace-Owner", admin.getId()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/tasks").with(appJwt(user)).header("X-Workspace-Owner", admin.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Forged\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", "bad-target"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId(), admin.getId()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/accounts/{id}/workspace/account", user.getId()).with(appJwt(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(user.getId()));
        mvc.perform(get("/api/v1/admin/accounts/{id}/workspace/account", user.getId()).with(appJwt(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledMembersRemainManageableButRevokedAdministratorsCannotWrite() throws Exception {
        user.setEnabled(false);
        users.saveAndFlush(user);
        mvc.perform(post("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Maintenance\",\"sortOrder\":0}"))
                .andExpect(status().isCreated());
        var oldSession = appJwt(admin);
        admin.changePassword(passwordEncoder.encode("new-admin-password"), false);
        users.saveAndFlush(admin);
        mvc.perform(post("/api/v1/tasks").with(oldSession).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Rejected\"}"))
                .andExpect(status().isUnauthorized());
        admin.requirePasswordChange();
        users.saveAndFlush(admin);
        mvc.perform(get("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        admin.setEnabled(false);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/v1/tasks").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorMarkdownUsesMemberVersionsHistoryTrashAndExports() throws Exception {
        var body = "{\"title\":\"Member note\",\"fileName\":\"member.md\",\"content\":\"Original content\",\"favorite\":false}";
        var created = mvc.perform(post("/api/v1/markdown-documents").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        Number version = JsonPath.read(created, "$.version");
        String update = body.substring(0, body.length() - 1).replace("Original content", "Updated content")
                + ",\"expectedVersion\":" + version + "}";
        mvc.perform(put("/api/v1/markdown-documents/" + id).with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/markdown-documents/" + id).with(appJwt(user))
                        .contentType(MediaType.APPLICATION_JSON).content(update.replace("Updated content", "Stale content")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/markdown-documents/" + id).with(appJwt(user)))
                .andExpect(jsonPath("$.content").value("Updated content"));
        mvc.perform(get("/api/v1/markdown-documents/" + id).with(appJwt(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/markdown-documents/" + id + "/revisions").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/v1/markdown-documents/export").with(appJwt(admin)).header("X-Workspace-Owner", user.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/zip"));
        mvc.perform(delete("/api/v1/markdown-documents/" + id).with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/markdown-documents/trash").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(post("/api/v1/markdown-documents/" + id + "/restore").with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void delegatedImagesAreUploadedToTheMemberAndCannotBeReadInAnotherWorkspace() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/markdown-images")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "image.png", "image/png", png))
                        .with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(result, "$.id");
        var record = images.findByIdAndOwnerId(id, user.getId()).orElseThrow();
        assertThat(images.findByIdAndOwnerId(id, admin.getId())).isEmpty();
        org.mockito.Mockito.doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(png);
            return null;
        }).when(imageStorage).transferTo(org.mockito.ArgumentMatchers.eq(record.getObjectKey()), org.mockito.ArgumentMatchers.any());
        mvc.perform(get("/api/v1/markdown-images/" + id).with(appJwt(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/markdown-images/" + id).with(appJwt(user)).header("X-Workspace-Owner", admin.getId()))
                .andExpect(status().isForbidden());
        var read = mvc.perform(get("/api/v1/markdown-images/" + id).with(appJwt(admin)).header("X-Workspace-Owner", user.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(read))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png))
                .andExpect(header().string("Vary", "Authorization, X-Workspace-Owner"));
    }

    private RequestPostProcessor appJwt(UserEntity account) {
        var authVersion = account.getAuthVersion();
        return jwt().jwt(token -> token
                .issuer("devnest-api")
                .subject(account.getId())
                .claim("auth_version", authVersion));
    }
}
