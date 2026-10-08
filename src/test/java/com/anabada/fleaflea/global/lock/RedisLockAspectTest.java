package com.anabada.fleaflea.global.lock;

import com.anabada.fleaflea.global.lock.exception.RedisLockBusyException;
import com.anabada.fleaflea.global.lock.exception.RedisLockUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.integration.support.locks.DistributedLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.integration.redis.util.RedisLockRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockAspectTest {

    @Mock
    private RedisLockRegistry redisLockRegistry;

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private DistributedLock lock;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RedisLockAspect redisLockAspect;
    private RedisLocked redisLocked;

    @BeforeEach
    void setUp() throws Exception {
        redisLockAspect = new RedisLockAspect(
                redisLockRegistry,
                beanFactory,
                meterRegistry,
                Duration.ofMillis(50)
        );
        redisLocked = LockedOperation.class.getMethod("updateMarket", Long.class).getAnnotation(RedisLocked.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new LockedOperation());
        when(joinPoint.getArgs()).thenReturn(new Object[]{10L});
        when(methodSignature.getMethod()).thenReturn(LockedOperation.class.getMethod("updateMarket", Long.class));
        when(redisLockRegistry.obtain("market:10")).thenReturn(lock);
    }

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("락 대기시간이 초과되면 비즈니스 로직을 실행하지 않는다")
    void executeWithLock_timeout_rejectsRequest() throws Throwable {
        when(lock.tryLock(50, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockBusyException.class);

        verify(joinPoint, never()).proceed();
        verify(lock, never()).unlock();
        assertThat(meterRegistry.get("redis.lock.acquire").tag("outcome", "timeout").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.find("redis.lock.hold").timer()).isNull();
    }

    @Test
    @DisplayName("Redis 연결이 실패하면 잠금 서비스 예외를 반환한다")
    void executeWithLock_redisFailure_rejectsRequest() throws Throwable {
        when(lock.tryLock(50, TimeUnit.MILLISECONDS))
                .thenThrow(new RedisConnectionFailureException("test failure"));

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockUnavailableException.class);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("락 대기가 중단되면 스레드의 인터럽트 상태를 유지한다")
    void executeWithLock_interrupted_preservesStatus() throws Throwable {
        when(lock.tryLock(50, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockUnavailableException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직이 실패해도 획득한 락을 해제한다")
    void executeWithLock_businessFailure_releasesLock() throws Throwable {
        when(lock.tryLock(50, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("test"));

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(IllegalArgumentException.class);

        verify(lock).unlock();
        assertThat(meterRegistry.get("redis.lock.acquire").tag("outcome", "acquired").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("redis.lock.hold").timer().count()).isEqualTo(1);
    }

    public static class LockedOperation {

        @RedisLocked(key = "'market:' + #marketId")
        public void updateMarket(Long marketId) {
        }
    }
}
