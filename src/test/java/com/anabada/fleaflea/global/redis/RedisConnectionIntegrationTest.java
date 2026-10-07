package com.anabada.fleaflea.global.redis;

import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = RedisConnectionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class RedisConnectionIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private String redisKey;

    @BeforeEach
    void setUp() {
        redisKey = "test:redis:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(redisKey);
    }

    @Test
    @DisplayName("설정된 비밀번호로 Redis에 연결한다")
    void connectRedis_withConfiguredPassword_returnsPong() {
        try (RedisConnection redisConnection = redisConnectionFactory.getConnection()) {
            assertThat(redisConnection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    @DisplayName("문자열과 TTL을 함께 저장하고 삭제한다")
    void storeRedisValue_withTimeToLive_preservesValueAndExpiration() {
        stringRedisTemplate.opsForValue().set(redisKey, "test-value", Duration.ofMinutes(1));

        assertThat(stringRedisTemplate.opsForValue().get(redisKey)).isEqualTo("test-value");
        assertThat(stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).isBetween(1L, 60L);

        assertThat(stringRedisTemplate.delete(redisKey)).isTrue();
        assertThat(stringRedisTemplate.hasKey(redisKey)).isFalse();
    }

    @Test
    @DisplayName("잘못된 비밀번호로 Redis를 사용할 수 없다")
    void connectRedis_withWrongPassword_throwsConnectionFailure() {
        LettuceConnectionFactory configuredConnectionFactory = (LettuceConnectionFactory) redisConnectionFactory;
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(
                configuredConnectionFactory.getHostName(),
                configuredConnectionFactory.getPort()
        );
        redisStandaloneConfiguration.setPassword("incorrect-password");
        LettuceConnectionFactory invalidConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        invalidConnectionFactory.afterPropertiesSet();

        try {
            assertThatThrownBy(() -> {
                try (RedisConnection redisConnection = invalidConnectionFactory.getConnection()) {
                    redisConnection.ping();
                }
            }).isInstanceOf(RedisConnectionFailureException.class);
        } finally {
            invalidConnectionFactory.destroy();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    @Import(RedisTestContainerConfiguration.class)
    static class TestApplication {
    }
}
