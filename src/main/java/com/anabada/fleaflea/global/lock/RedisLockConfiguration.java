package com.anabada.fleaflea.global.lock;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class RedisLockConfiguration {

    @Bean
    public ThreadPoolTaskScheduler redisLockRenewalScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("redis-lock-renewal-");
        return scheduler;
    }

    @Bean(destroyMethod = "destroy")
    public RedisLockRegistry redisLockRegistry(
            RedisConnectionFactory redisConnectionFactory,
            ThreadPoolTaskScheduler redisLockRenewalScheduler,
            @Value("${app.redis-lock.lease-time:60s}") Duration leaseTime
    ) {
        if (leaseTime.toMillis() < 3000) {
            throw new IllegalArgumentException("Redis lock lease time must be at least 3 seconds");
        }

        RedisLockRegistry redisLockRegistry = new RedisLockRegistry(
                redisConnectionFactory, "fleaflea:lock", leaseTime.toMillis()
        );
        redisLockRegistry.setRenewalTaskScheduler(redisLockRenewalScheduler);
        return redisLockRegistry;
    }
}
