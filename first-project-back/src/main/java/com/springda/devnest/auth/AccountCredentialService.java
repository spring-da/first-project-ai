package com.springda.devnest.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AccountCredentialService {

    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%*-_".toCharArray();
    private static final char[] ALL = (new String(UPPER) + new String(LOWER)
            + new String(DIGITS) + new String(SYMBOLS)).toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String newInvitationToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashInvitationToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String newTemporaryPassword() {
        var password = new char[20];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        password[3] = pick(SYMBOLS);
        for (int index = 4; index < password.length; index++) {
            password[index] = pick(ALL);
        }
        for (int index = password.length - 1; index > 0; index--) {
            var swapIndex = random.nextInt(index + 1);
            var value = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = value;
        }
        return new String(password);
    }

    private char pick(char[] alphabet) {
        return alphabet[random.nextInt(alphabet.length)];
    }
}
