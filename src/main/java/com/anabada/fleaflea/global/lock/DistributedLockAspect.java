package com.anabada.fleaflea.global.lock;

import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.lock.exception.DistributedLockBusyException;
import com.anabada.fleaflea.global.lock.exception.DistributedLockUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DistributedLockAspect {

    private static final String DISTRIBUTED_LOCK_PREFIX = "fleaflea:redisson:lock:";

    private final RedissonClient redissonClient;
    private final BeanFactory beanFactory;
    private final MeterRegistry meterRegistry;
    private final Duration waitTime;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(
            @Lazy RedissonClient redissonClient,
            BeanFactory beanFactory,
            MeterRegistry meterRegistry,
            @Value("${app.redis-lock.wait-time:5s}") Duration waitTime
    ) {
        if (waitTime.isNegative()) {
            throw new IllegalArgumentException("Redis lock wait time must not be negative");
        }
        this.redissonClient = redissonClient;
        this.beanFactory = beanFactory;
        this.meterRegistry = meterRegistry;
        this.waitTime = waitTime;
    }

    @Around("@annotation(distributedLock)")
    public Object executeWithLock(
            ProceedingJoinPoint joinPoint,
            DistributedLock distributedLock
    ) throws Throwable {
        List<String> lockKeys = getLockKeys(joinPoint, distributedLock);
        List<RLock> locks = new ArrayList<>();
        long deadline = System.nanoTime() + waitTime.toNanos();
        try {
            for (String key : lockKeys) {
                locks.add(acquireLock(key, Math.max(0, deadline - System.nanoTime())));
            }
        } catch (Throwable failure) {
            releaseLocks(locks, System.nanoTime());
            throw failure;
        }
        long acquiredAt = System.nanoTime();
        boolean releaseAfterTransaction = false;

        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseLocks(locks, acquiredAt);
                    }
                });
                releaseAfterTransaction = true;
            }
            return joinPoint.proceed();
        } finally {
            if (!releaseAfterTransaction) {
                releaseLocks(locks, acquiredAt);
            }
        }
    }

    private List<String> getLockKeys(
            ProceedingJoinPoint joinPoint,
            DistributedLock distributedLock
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer
        );
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        Object value;
        try {
            value = expressionParser.parseExpression(distributedLock.key()).getValue(context);
        } catch (SpelEvaluationException exception) {
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof BusinessException businessException) {
                    throw businessException;
                }
                cause = cause.getCause();
            }
            throw exception;
        }

        Collection<?> keys = value instanceof Collection<?> collection ? collection : Collections.singletonList(value);
        return keys.stream().map(key -> {
            if (!(key instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("Redis lock key must not be blank");
            }
            return text;
        }).distinct().sorted().toList();
    }

    private RLock acquireLock(
            String lockKey,
            long remainingNanos
    ) {
        long startedAt = System.nanoTime();
        String outcome = "unavailable";

        try {
            RLock lock = redissonClient.getLock(DISTRIBUTED_LOCK_PREFIX + lockKey);
            if (!lock.tryLock(remainingNanos, TimeUnit.NANOSECONDS)) {
                outcome = "timeout";
                throw new DistributedLockBusyException();
            }
            outcome = "acquired";
            return lock;
        } catch (InterruptedException exception) {
            outcome = "interrupted";
            Thread.currentThread().interrupt();
            throw new DistributedLockUnavailableException(exception);
        } catch (DistributedLockBusyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DistributedLockUnavailableException(exception);
        } finally {
            try {
                Timer.builder("redis.lock.acquire")
                        .tag("outcome", outcome)
                        .register(meterRegistry)
                        .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            } catch (RuntimeException exception) {
                log.warn("Redis 락 획득 시간 계측 실패: outcome={}", outcome, exception);
            }
        }
    }

    private void releaseLocks(
            List<RLock> locks,
            long acquiredAt
    ) {
        if (locks.isEmpty()) {
            return;
        }
        try {
            meterRegistry.timer("redis.lock.hold")
                    .record(System.nanoTime() - acquiredAt, TimeUnit.NANOSECONDS);
        } catch (RuntimeException exception) {
            log.warn("Redis 락 보유 시간 계측 실패", exception);
        }
        for (int i = locks.size() - 1; i >= 0; i--) {
            RLock lock = locks.get(i);
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                } else {
                    log.error("Redis 락 소유권 상실: lockKey={}", lock.getName());
                }
            } catch (RuntimeException exception) {
                // 커밋된 요청의 결과를 바꾸지 않는다. 잔여 락은 watchdog TTL로 만료된다.
                log.error("Redis 락 해제 실패: lockKey={}", lock.getName(), exception);
            }
        }
    }
}
