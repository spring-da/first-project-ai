package com.springda.devnest.image;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

@Component
@EnableConfigurationProperties(OssProperties.class)
public class OssImageStorage implements ImageStorage {
    private final OssProperties properties;
    private volatile OSS client;

    public OssImageStorage(OssProperties properties) { this.properties = properties; }

    // Lazy initialization lets every other feature work before OSS is configured.
    private synchronized OSS client() {
        if (client != null) return client;
        if (!properties.enabled() || blank(properties.endpoint()) || blank(properties.region())
                || blank(properties.bucket()) || blank(properties.accessKeyId()) || blank(properties.accessKeySecret())) {
            throw new ImageStorageException("图片上传尚未配置，请先在后端环境变量中配置阿里云 OSS。");
        }
        try {
            URI endpoint = URI.create(properties.endpoint());
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("Invalid OSS endpoint");
            }
            var config = new ClientBuilderConfiguration();
            config.setSignatureVersion(SignVersion.V4);
            config.setConnectionTimeout(5_000);
            config.setSocketTimeout(30_000);
            config.setConnectionRequestTimeout(5_000);
            config.setMaxErrorRetry(1);
            config.setMaxConnections(32);
            var credentials = blank(properties.securityToken())
                    ? new DefaultCredentialProvider(properties.accessKeyId(), properties.accessKeySecret())
                    : new DefaultCredentialProvider(properties.accessKeyId(), properties.accessKeySecret(), properties.securityToken());
            client = OSSClientBuilder.create().endpoint(properties.endpoint()).region(properties.region())
                    .credentialsProvider(credentials).clientConfiguration(config).build();
            return client;
        } catch (RuntimeException exception) {
            throw new ImageStorageException("OSS 配置不正确，请检查 HTTPS Endpoint、地域和访问凭证。");
        }
    }

    @Override public void put(String key, byte[] bytes, String contentType) {
        OSS oss = client();
        try {
            var metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(bytes.length);
            metadata.setContentDisposition("inline");
            // Object keys are immutable UUID names. Let browsers and edge caches reuse public
            // objects, while private deployments keep the metadata explicitly private.
            metadata.setCacheControl(properties.publicRead()
                    ? "public, max-age=31536000, immutable"
                    : "private, max-age=31536000, immutable");
            // Public-read deployments inherit the Bucket ACL. Private deployments keep an explicit
            // object ACL so a later Bucket policy change cannot accidentally expose old uploads.
            if (!properties.publicRead()) metadata.setHeader("x-oss-object-acl", "private");
            oss.putObject(properties.bucket(), key, new ByteArrayInputStream(bytes), metadata);
        } catch (RuntimeException exception) {
            // SDK exceptions can contain request URLs/credential IDs: do not return them to clients.
            throw new ImageStorageException("图片上传失败，请检查 OSS 配置、写入权限或网络后重试。");
        }
    }

    @Override public byte[] get(String key) {
        var output = new ByteArrayOutputStream();
        transferTo(key, output);
        return output.toByteArray();
    }

    @Override public void transferTo(String key, OutputStream target) {
        OSS oss = client();
        try (var object = oss.getObject(properties.bucket(), key);
             var input = object.getObjectContent()) {
            long declaredLength = object.getObjectMetadata().getContentLength();
            if (declaredLength > MarkdownImageService.MAX_IMAGE_BYTES) {
                throw new IllegalStateException("Oversized object");
            }
            byte[] buffer = new byte[64 * 1024];
            long transferred = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                transferred += read;
                if (transferred > MarkdownImageService.MAX_IMAGE_BYTES) {
                    throw new IllegalStateException("Oversized object");
                }
                target.write(buffer, 0, read);
            }
        } catch (Exception exception) {
            throw new ImageStorageException("图片暂时无法读取，请稍后重试或检查 OSS 读取权限。");
        }
    }

    @Override public void delete(String key) { client().deleteObject(properties.bucket(), key); }
    @PreDestroy public void close() { if (client != null) client.shutdown(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
