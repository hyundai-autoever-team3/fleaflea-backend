package com.anabada.fleaflea.domain.chat.config;

import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chat.redis")
public record ChatRedisProperties(
        @NotBlank
        String channelPrefix
) {

    public String channelFor(ChatEventType chatEventType) {
        return channelPrefix + ":" + chatEventType.getValue();
    }
}
