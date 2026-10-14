package com.anabada.fleaflea.global.lock;

import com.anabada.fleaflea.global.lock.exception.RedisLockBusyException;
import com.anabada.fleaflea.global.lock.exception.RedisLockUnavailableException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockAspectTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private RLock lock;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RedisLockAspect redisLockAspect;
    private RedisLocked redisLocked;

    @BeforeEach
    void setUp() throws Exception {
        redisLockAspect = new RedisLockAspect(
                redissonClient,
                beanFactory,
                meterRegistry,
                Duration.ofMillis(50)
        );
        redisLocked = LockedOperation.class.getMethod("updateMarket", Long.class).getAnnotation(RedisLocked.class);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new LockedOperation());
        when(joinPoint.getArgs()).thenReturn(new Object[]{10L});
        when(methodSignature.getMethod()).thenReturn(LockedOperation.class.getMethod("updateMarket", Long.class));
        lenient().when(redissonClient.getLock("fleaflea:redisson:lock:market:10")).thenReturn(lock);
    }

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("락 대기시간이 초과되면 비즈니스 로직을 실행하지 않는다")
    void executeWithLock_timeout_rejectsRequest() throws Throwable {
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);

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
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new RedisConnectionFailureException("test failure"));

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockUnavailableException.class);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("락 대기가 중단되면 스레드의 인터럽트 상태를 유지한다")
    void executeWithLock_interrupted_preservesStatus() throws Throwable {
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockUnavailableException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직이 실패해도 획득한 락을 해제한다")
    void executeWithLock_businessFailure_releasesLock() throws Throwable {
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("test"));

        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(IllegalArgumentException.class);

        verify(lock).unlock();
        assertThat(meterRegistry.get("redis.lock.acquire").tag("outcome", "acquired").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("redis.lock.hold").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("계측이 실패해도 작업 결과를 반환하고 획득한 락을 해제한다")
    void executeWithLock_metricsFailure_preservesResultAndReleasesLock() throws Throwable {
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id meterId) {
                throw new IllegalStateException("metrics unavailable");
            }
        });
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("updated");

        Object result = redisLockAspect.executeWithLock(joinPoint, redisLocked);

        assertThat(result).isEqualTo("updated");
        verify(lock).unlock();
    }

    @Test
    @DisplayName("여러 키는 중복 제거·정렬하여 획득하고 역순 해제한다")
    void executeWithLock_multipleKeys_ordersAndDeduplicates() throws Throwable {
        useMultipleKeys();
        RLock second = mock(RLock.class);
        when(redissonClient.getLock("fleaflea:redisson:lock:a")).thenReturn(lock);
        when(redissonClient.getLock("fleaflea:redisson:lock:b")).thenReturn(second);
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(second.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(second.isHeldByCurrentThread()).thenReturn(true);
        redisLockAspect.executeWithLock(joinPoint, redisLocked);
        var ordered = inOrder(lock, second, joinPoint);
        ordered.verify(lock).tryLock(anyLong(), eq(TimeUnit.NANOSECONDS));
        ordered.verify(second).tryLock(anyLong(), eq(TimeUnit.NANOSECONDS));
        ordered.verify(joinPoint).proceed();
        ordered.verify(second).unlock();
        ordered.verify(lock).unlock();
    }

    @Test
    @DisplayName("여러 키의 일부만 획득한 뒤 실패하면 획득한 락을 해제한다")
    void executeWithLock_partialAcquisition_releasesEarlierLocks() throws Throwable {
        useMultipleKeys();
        RLock second = mock(RLock.class);
        when(redissonClient.getLock("fleaflea:redisson:lock:a")).thenReturn(lock);
        when(redissonClient.getLock("fleaflea:redisson:lock:b")).thenReturn(second);
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(second.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        assertThatThrownBy(() -> redisLockAspect.executeWithLock(joinPoint, redisLocked))
                .isInstanceOf(RedisLockBusyException.class);
        verify(lock).unlock();
        verify(second, never()).unlock();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("소유권이 사라진 락은 다른 소유자를 위해 해제하지 않는다")
    void executeWithLock_lostOwnership_doesNotUnlock() throws Throwable {
        when(lock.tryLock(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        redisLockAspect.executeWithLock(joinPoint, redisLocked);
        verify(lock, never()).unlock();
    }

    private void useMultipleKeys() throws Exception {
        var method = LockedOperation.class.getMethod("multiple", java.util.List.class);
        redisLocked = method.getAnnotation(RedisLocked.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{java.util.List.of("b", "a", "a")});
    }

    public static class LockedOperation {
        @RedisLocked(key = "#keys")
        public void multiple(java.util.List<String> keys) {
        }


        @RedisLocked(key = "'market:' + #marketId")
        public void updateMarket(Long marketId) {
        }
    }
}
