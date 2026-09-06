package com.springda.devnest.community;

import com.jayway.jsonpath.JsonPath;
import com.springda.devnest.admin.AdminAuditEventRepository;
import com.springda.devnest.image.ImageStorage;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:communication;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItY29tbXVuaWNhdGlvbi10ZXN0cw==",
        "app.security.issuer=devnest-api",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false",
        "app.oss.prefix=markdown/images"
})
@Transactional
class CommunicationIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CommunityMessageRepository messages;
    @Autowired AdminAuditEventRepository auditEvents;
    @MockitoBean ImageStorage storage;
    private MockMvc mvc;
    private UserEntity admin;
    private UserEntity member;
    private UserEntity anotherMember;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.save(new UserEntity(
                "admin@example.com", passwordEncoder.encode("admin-password"), "Administrator", UserRole.ADMIN));
        member = users.save(new UserEntity(
                "member@example.com", passwordEncoder.encode("member-password"), "Member"));
        anotherMember = users.save(new UserEntity(
                "another@example.com", passwordEncoder.encode("member-password"), "Another member"));
    }

    @Test
    void announcementIsShownOnceToEveryMemberAndCanBeArchivedByAnAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/announcements").with(appJwt(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Maintenance\",\"content\":\"The system was upgraded.\"}"))
                .andExpect(status().isForbidden());

        var published = mvc.perform(post("/api/v1/admin/announcements").with(appJwt(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Maintenance\",\"content\":\"The system was upgraded.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String announcementId = JsonPath.read(published, "$.id");

        mvc.perform(get("/api/v1/announcements/unread").with(appJwt(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(announcementId))
                .andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(get("/api/v1/announcements/unread").with(appJwt(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/v1/announcements/{id}/read", announcementId).with(appJwt(member)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/announcements/{id}/read", announcementId).with(appJwt(member)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/announcements/unread").with(appJwt(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/announcements/unread").with(appJwt(anotherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(announcementId));

        mvc.perform(delete("/api/v1/admin/announcements/{id}", announcementId).with(appJwt(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/announcements").with(appJwt(anotherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/admin/announcements").with(appJwt(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        assertThat(auditEvents.count()).isEqualTo(2);
    }

    @Test
    void messagesUseCursorPaginationAndLoadRepliesAndImagesOnDemand() throws Exception {
        mvc.perform(multipart("/api/v1/community/messages").param("content", "Anonymous"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/community/messages").with(appJwt(member)))
                .andExpect(status().isBadRequest());

        String firstId = createMessage(member, "First idea", null);
        String secondId = createMessage(anotherMember, "Second idea", null);
        String thirdId = createMessage(member, "A screenshot", PNG);

        var firstPage = mvc.perform(get("/api/v1/community/messages?size=2").with(appJwt(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");
        // A managed Instant can retain finer precision than the database column. The cursor
        // boundary must come from the database so the boundary row cannot reappear on page 2.
        var managedBoundary = messages.findById(cursor).orElseThrow();
        ReflectionTestUtils.setField(
                managedBoundary, "createdAt", managedBoundary.getCreatedAt().plusNanos(999));
        var secondPage = mvc.perform(get("/api/v1/community/messages?size=2&cursor={cursor}", cursor)
                        .with(appJwt(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(firstPage + secondPage).contains(firstId, secondId, thirdId);

        var reply = mvc.perform(multipart("/api/v1/community/messages/{id}/replies", firstId)
                        .file(new MockMultipartFile("image", "reply.png", "image/png", PNG))
                        .param("content", "I agree")
                        .with(appJwt(anotherMember)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(firstId))
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String replyId = JsonPath.read(reply, "$.id");
        assertThat((String) JsonPath.read(reply, "$.imageUrl"))
                .isEqualTo("/community/messages/" + replyId + "/image");

        mvc.perform(get("/api/v1/community/messages").with(appJwt(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')].replyCount".formatted(firstId)).value(1));
        mvc.perform(get("/api/v1/community/messages/{id}/replies", firstId).with(appJwt(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(replyId))
                .andExpect(jsonPath("$.items[0].viewerCanDelete").value(false));

        var imageRecord = messages.findById(replyId).orElseThrow();
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(PNG);
            return null;
        }).when(storage).transferTo(eq(imageRecord.getImageObjectKey()), any(java.io.OutputStream.class));
        var imageRequest = mvc.perform(get("/api/v1/community/messages/{id}/image", replyId)
                        .with(appJwt(member)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(imageRequest))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "private, max-age=3600, immutable"));

        mvc.perform(delete("/api/v1/community/messages/{id}", replyId).with(appJwt(member)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/community/messages/{id}", replyId).with(appJwt(anotherMember)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/community/messages/{id}", secondId).with(appJwt(admin)))
                .andExpect(status().isNoContent());
        assertThat(auditEvents.count()).isEqualTo(1);
    }

    private String createMessage(UserEntity author, String content, byte[] image) throws Exception {
        var request = multipart("/api/v1/community/messages").param("content", content).with(appJwt(author));
        if (image != null) request.file(new MockMultipartFile("image", "screen.png", "image/png", image));
        var response = mvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private RequestPostProcessor appJwt(UserEntity account) {
        return jwt().jwt(token -> token.issuer("devnest-api").subject(account.getId())
                .claim("auth_version", account.getAuthVersion()));
    }
}
