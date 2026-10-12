package com.anabada.fleaflea.global.image;

public interface ImageStorage {

    void upload(String imageKey, byte[] imageBytes, String contentType);

    void delete(String imageKey);

    void copy(String sourceKey, String targetKey);

    String getUrl(String imageKey);
}
