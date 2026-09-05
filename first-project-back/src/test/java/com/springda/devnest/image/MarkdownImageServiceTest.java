package com.springda.devnest.image;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarkdownImageServiceTest {
    private final OssProperties properties = new OssProperties(false, "", "", "private-bucket", "", "", "", "markdown/images", ZoneId.of("Asia/Shanghai"), false);

    @Test void unconfiguredOssFailsLazilyWithAnActionableMessage() {
        var storage = new OssImageStorage(properties);
        assertThatThrownBy(() -> storage.put("key", new byte[0], "image/png"))
                .isInstanceOf(ImageStorageException.class).hasMessageContaining("环境变量");
    }

    @Test void fileSignaturesDetermineTheAllowedImageType() {
        assertThat(MarkdownImageService.detectFormat(new byte[]{(byte)255, (byte)216, (byte)255})).isEqualTo("jpg");
        assertThat(MarkdownImageService.detectFormat("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEqualTo("gif");
        assertThat(MarkdownImageService.detectFormat("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII))).isEqualTo("webp");
        assertThatThrownBy(() -> MarkdownImageService.detectFormat("<html/>".getBytes())).hasMessageContaining("格式");
    }

    @Test void objectKeysUseAnAccountScopedCalendarDatePath() {
        assertThat(MarkdownImageService.objectKey("markdown/images", "owner-one", LocalDate.of(2026, 8, 29), "项目架构图", "object-id", "png"))
                .isEqualTo("markdown/images/owner-one/2026/08/29/项目架构图_object-id.png");
    }

    @Test void originalFileNamesAreReadableButCannotControlTheObjectPathOrExtension() {
        assertThat(MarkdownImageService.safeFileBase("../../项目 架构图.svg")).isEqualTo("项目_架构图");
        assertThat(MarkdownImageService.safeFileBase("C:\\Users\\SpringDa\\截图 01.PNG")).isEqualTo("截图_01");
        assertThat(MarkdownImageService.safeFileBase("../...svg")).isEqualTo("image");
    }

    @Test void ossObjectsAreUploadedAsPrivateWithTheValidatedContentType() {
        OSS client = mock(OSS.class);
        var storage = new OssImageStorage(properties);
        ReflectionTestUtils.setField(storage, "client", client);
        storage.put("markdown/images/user/test.png", new byte[]{1, 2, 3}, "image/png");
        var metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("private-bucket"), eq("markdown/images/user/test.png"), any(java.io.InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/png");
        assertThat(metadata.getValue().getCacheControl()).isEqualTo("private, max-age=31536000, immutable");
        assertThat(metadata.getValue().getContentDisposition()).isEqualTo("inline");
        assertThat(metadata.getValue().getRawMetadata()).containsEntry("x-oss-object-acl", "private");
    }

    @Test void publicReadModeInheritsTheBucketAclInsteadOfForcingPrivateObjects() {
        var publicProperties = new OssProperties(
                false, "", "", "public-read-bucket", "", "", "", "markdown/images",
                ZoneId.of("Asia/Shanghai"), true);
        OSS client = mock(OSS.class);
        var storage = new OssImageStorage(publicProperties);
        ReflectionTestUtils.setField(storage, "client", client);

        storage.put("markdown/images/user/test.png", new byte[]{1, 2, 3}, "image/png");

        var metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("public-read-bucket"), eq("markdown/images/user/test.png"),
                any(java.io.InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getCacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(metadata.getValue().getRawMetadata()).doesNotContainKey("x-oss-object-acl");
    }

    @Test void databaseFailureCleansUpTheUploadedObject() {
        var repository = mock(MarkdownImageRepository.class);
        var storage = mock(ImageStorage.class);
        var service = new MarkdownImageService(repository, storage, properties);
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.upload("owner-one", new MockMultipartFile("file", "image.gif", "image/gif", "GIF89a".getBytes())))
                .isInstanceOf(ImageStorageException.class).hasMessageContaining("记录保存失败");
        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), eq("image/gif"));
        verify(storage).delete(key.getValue());
    }
}
