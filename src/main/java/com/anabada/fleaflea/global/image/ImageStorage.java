package com.anabada.fleaflea.global.image;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public interface ImageStorage {

    default Map<String, String> getListUrls(Collection<String> imageKeys) {
        Map<String, String> imageUrls = new HashMap<>();
        imageKeys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(imageKey -> imageUrls.put(imageKey, getUrl(imageKey)));
        return imageUrls;
    }

    void upload(String imageKey, byte[] imageBytes, String contentType);

    void delete(String imageKey);

    void copy(String sourceKey, String targetKey);

    String getUrl(String imageKey);
}
