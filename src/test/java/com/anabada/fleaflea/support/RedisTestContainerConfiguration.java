package com.anabada.fleaflea.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

@TestConfiguration(proxyBeanMethods = false)
public class RedisTestContainerConfiguration {

    private static final String REDIS_PASSWORD = "redis-test-password";
    private static final int REDIS_PORT = 6379;

    @Bean(initMethod = "start", destroyMethod = "stop")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7.4")
                .withExposedPorts(REDIS_PORT)
                .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);
    }

    @Bean
    DynamicPropertyRegistrar redisProperties(@Qualifier("redisContainer") GenericContainer<?> redisContainer) {
        return registry -> {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(REDIS_PORT));
            registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        };
    }
}
