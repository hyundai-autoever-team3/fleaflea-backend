package com.anabada.fleaflea.global.lock;

import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class RedisLockConfiguration {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            DataRedisProperties properties,
            @Value("${app.redis-lock.lease-time:60s}") Duration watchdogTimeout
    ) {
        if (watchdogTimeout.toMillis() < 3000) {
            throw new IllegalArgumentException("Redis lock watchdog timeout must be at least 3 seconds");
        }
        Config config = new Config();
        config.setLockWatchdogTimeout(watchdogTimeout.toMillis());
        var server = config.useSingleServer()
                .setAddress((properties.getSsl().isEnabled() ? "rediss://" : "redis://")
                        + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(4);
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            server.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            server.setPassword(properties.getPassword());
        }
        if (properties.getConnectTimeout() != null) {
            server.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        }
        if (properties.getTimeout() != null) {
            server.setTimeout(Math.toIntExact(properties.getTimeout().toMillis()));
        }
        return Redisson.create(config);
    }
}
