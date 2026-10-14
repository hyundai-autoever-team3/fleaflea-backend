package com.anabada.fleaflea.global.image;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public interface ImageStorage {

    default Map<String, String> getListUrls(Collection<String> keys) {
        Map<String, String> urls = new HashMap<>();
        keys.stream().filter(Objects::nonNull).distinct()
                .forEach(key -> urls.put(key, getUrl(key)));
        return urls;
    }

    void upload(String imageKey, byte[] imageBytes, String contentType);

    void delete(String imageKey);

    void copy(String sourceKey, String targetKey);

    String getUrl(String imageKey);
}
