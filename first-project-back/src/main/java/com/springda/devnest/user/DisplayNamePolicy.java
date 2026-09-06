package com.springda.devnest.user;

import com.springda.devnest.common.BadRequestException;

import java.text.Normalizer;
import java.util.Locale;

public final class DisplayNamePolicy {
    private static final int MAX_LENGTH = 80;

    private DisplayNamePolicy() {
    }

    public static String normalize(String value) {
        var normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) throw new BadRequestException("昵称不能为空");
        if (normalized.length() > MAX_LENGTH) throw new BadRequestException("昵称不能超过 80 个字符");
        return normalized;
    }

    public static String key(String normalizedName) {
        return normalizedName.toLowerCase(Locale.ROOT);
    }
}
