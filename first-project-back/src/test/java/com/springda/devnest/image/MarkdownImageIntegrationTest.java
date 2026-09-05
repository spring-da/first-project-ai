package com.springda.devnest.image;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:markdown-images;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.security.jwt-secret-base64=dGVzdC1vbmx5LXNlY3JldC1mb3ItbWFya2Rvd24tcmVjb3ZlcnktdGVzdHM=",
        "app.account-security.bootstrap-admin-required=false",
        "app.cors.allowed-origins=http://localhost:5173", "app.oss.enabled=false",
        "app.oss.prefix=markdown/images"
})
@Transactional
class MarkdownImageIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/XxoAAAAASUVORK5CYII=");
    @Autowired WebApplicationContext context;
    @Autowired MarkdownImageRepository images;
    @MockitoBean ImageStorage storage;
    private MockMvc mvc;

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void uploadAndReadAreAuthenticatedAndIsolatedByOwner() throws Exception {
        var file = new MockMultipartFile("file", "../../wrong.svg", "text/html", PNG);
        mvc.perform(multipart("/api/v1/markdown-images").file(file)).andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);

        var response = mvc.perform(multipart("/api/v1/markdown-images").file(file).with(jwt().jwt(j -> j.subject("owner-one"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        String url = JsonPath.read(response, "$.url");
        assertThat(url).isEqualTo("/api/v1/markdown-images/" + id);
        assertThat(response).doesNotContain("objectKey", "AccessKey", "Signature", "owner-one");
        var record = images.findByIdAndOwnerId(id, "owner-one").orElseThrow();
        String datePath = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString().replace('-', '/');
        assertThat(record.getObjectKey()).startsWith("markdown/images/owner-one/" + datePath + "/")
                .containsPattern("/wrong_[0-9a-f-]{36}\\.png$").doesNotContain("..", "wrong.svg");
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(PNG);
            return null;
        }).when(storage).transferTo(eq(record.getObjectKey()), any(java.io.OutputStream.class));
        var download = mvc.perform(get(url).with(jwt().jwt(j -> j.subject("owner-one"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(download))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "private, max-age=3600, immutable"))
                .andExpect(header().string("Vary", "Authorization, X-Workspace-Owner"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        clearInvocations(storage);
        mvc.perform(get(url).with(jwt().jwt(j -> j.subject("owner-two")))).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test void rejectsMissingEmptyOversizedAndDisguisedActiveContent() throws Exception {
        var user = jwt().jwt(j -> j.subject("owner-one"));
        mvc.perform(multipart("/api/v1/markdown-images").with(user)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/markdown-images").file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])).with(user)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/markdown-images").file(new MockMultipartFile("file", "fake.png", "image/png", "<svg onload='alert(1)'/>".getBytes())).with(user)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/markdown-images").file(new MockMultipartFile("file", "large.png", "image/png", new byte[MarkdownImageService.MAX_IMAGE_BYTES + 1])).with(user)).andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
        assertThat(images.count()).isZero();
    }

    @Test void storageFailuresReturnReadableErrorsWithoutCreatingMetadata() throws Exception {
        doThrow(new ImageStorageException("图片上传尚未配置，请先配置阿里云 OSS。")).when(storage).put(anyString(), any(), anyString());
        mvc.perform(multipart("/api/v1/markdown-images").file(new MockMultipartFile("file", "test.png", "image/png", PNG)).with(jwt().jwt(j -> j.subject("owner-one"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("图片上传尚未配置，请先配置阿里云 OSS。"));
        assertThat(images.count()).isZero();
    }
}
