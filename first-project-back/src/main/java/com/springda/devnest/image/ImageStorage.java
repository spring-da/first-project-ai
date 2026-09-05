package com.springda.devnest.image;

import java.io.IOException;
import java.io.OutputStream;

public interface ImageStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
    default void transferTo(String key, OutputStream target) {
        try {
            target.write(get(key));
        } catch (IOException exception) {
            throw new ImageStorageException("图片传输中断，请稍后重试。");
        }
    }
    void delete(String key);
}
