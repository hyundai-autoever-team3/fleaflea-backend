package com.anabada.fleaflea.domain.chat.config;

import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import com.anabada.fleaflea.domain.chat.redis.RedisChatEventSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.chat.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChatRedisProperties.class)
public class ChatRedisConfiguration {

    @Bean
    public RedisMessageListenerContainer chatRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisChatEventSubscriber redisChatEventSubscriber,
            ChatRedisProperties chatRedisProperties
    ) {
        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisConnectionFactory);
        List<ChannelTopic> topics = Arrays.stream(ChatEventType.values())
                .map(chatRedisProperties::channelFor)
                .map(ChannelTopic::new)
                .toList();
        listenerContainer.addMessageListener(redisChatEventSubscriber, topics);

        return listenerContainer;
    }
}
