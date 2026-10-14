package com.anabada.fleaflea.global.lock;

import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class RedisLockConfiguration {

    private static final long MINIMUM_WATCHDOG_TIMEOUT_MILLIS = 3000;

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            DataRedisProperties properties,
            @Value("${app.redis-lock.lease-time:60s}") Duration watchdogTimeout
    ) {
        if (watchdogTimeout.toMillis() < MINIMUM_WATCHDOG_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("Redis lock watchdog timeout must be at least 3 seconds");
        }
        Config config = new Config();
        config.setLockWatchdogTimeout(watchdogTimeout.toMillis());
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress((properties.getSsl().isEnabled() ? "rediss://" : "redis://")
                        + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(4);
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            serverConfig.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            serverConfig.setPassword(properties.getPassword());
        }
        if (properties.getConnectTimeout() != null) {
            serverConfig.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        }
        if (properties.getTimeout() != null) {
            serverConfig.setTimeout(Math.toIntExact(properties.getTimeout().toMillis()));
        }
        return Redisson.create(config);
    }
}
