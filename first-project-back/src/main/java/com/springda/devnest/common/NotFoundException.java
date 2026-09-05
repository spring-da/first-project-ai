package com.springda.devnest.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String resource, String id) {
        super("%s 不存在：%s".formatted(resource, id));
    }
}
