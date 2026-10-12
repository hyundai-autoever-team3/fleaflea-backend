package com.anabada.fleaflea.global.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.image.storage", havingValue = "gcs")
public class GcsConfig {

    @Bean
    public Storage gcsStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
