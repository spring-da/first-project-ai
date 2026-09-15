package com.springda.devnest.sharing;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts recoverable share tokens at rest without changing their public lookup digest. */
@Component
public final class ShareBundleTokenProtector {
    private static final String VERSION = "v1";
    private static final byte[] AAD = "devnest:share-bundle-token:v1".getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecretKey encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public ShareBundleTokenProtector(SecretKey sourceKey) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(concat(AAD, sourceKey.getEncoded()));
            this.encryptionKey = new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化分享链接保护器", exception);
        }
    }

    public String protect(String token) {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return VERSION + "." + ENCODER.encodeToString(iv) + "." + ENCODER.encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法加密分享链接凭据", exception);
        }
    }

    public String reveal(String protectedToken) {
        try {
            String[] parts = protectedToken.split("\\.", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw new IllegalArgumentException("unsupported token envelope");
            byte[] iv = DECODER.decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException("invalid token nonce");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(DECODER.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法解密分享链接凭据", exception);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] value = new byte[left.length + right.length];
        System.arraycopy(left, 0, value, 0, left.length);
        System.arraycopy(right, 0, value, left.length, right.length);
        return value;
    }
}
