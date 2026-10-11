package com.anabada.fleaflea.global.lock;

import com.anabada.fleaflea.global.lock.exception.RedisLockBusyException;
import com.anabada.fleaflea.global.lock.exception.RedisLockUnavailableException;
import com.anabada.fleaflea.global.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RedisLockAspect {

    private final RedisLockRegistry redisLockRegistry;
    private final BeanFactory beanFactory;
    private final MeterRegistry meterRegistry;
    private final Duration waitTime;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public RedisLockAspect(
            RedisLockRegistry redisLockRegistry,
            BeanFactory beanFactory,
            MeterRegistry meterRegistry,
            @Value("${app.redis-lock.wait-time:5s}") Duration waitTime
    ) {
        if (waitTime.isNegative()) {
            throw new IllegalArgumentException("Redis lock wait time must not be negative");
        }
        this.redisLockRegistry = redisLockRegistry;
        this.beanFactory = beanFactory;
        this.meterRegistry = meterRegistry;
        this.waitTime = waitTime;
    }

    @Around("@annotation(redisLocked)")
    public Object executeWithLock(
            ProceedingJoinPoint joinPoint,
            RedisLocked redisLocked
    ) throws Throwable {
        String lockKey = getLockKey(joinPoint, redisLocked);
        Lock lock = acquireLock(lockKey);
        long acquiredAt = System.nanoTime();
        boolean releaseAfterTransaction = false;

        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseLock(lock, lockKey, acquiredAt);
                    }
                });
                releaseAfterTransaction = true;
            }
            return joinPoint.proceed();
        } finally {
            if (!releaseAfterTransaction) {
                releaseLock(lock, lockKey, acquiredAt);
            }
        }
    }

    private String getLockKey(
            ProceedingJoinPoint joinPoint,
            RedisLocked redisLocked
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer
        );
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        String lockKey;
        try {
            lockKey = expressionParser.parseExpression(redisLocked.key()).getValue(context, String.class);
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

        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("Redis lock key must not be blank");
        }
        return lockKey;
    }

    private Lock acquireLock(String lockKey) {
        long startedAt = System.nanoTime();
        String outcome = "unavailable";

        try {
            Lock lock = redisLockRegistry.obtain(lockKey);
            if (!lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS)) {
                outcome = "timeout";
                throw new RedisLockBusyException();
            }
            outcome = "acquired";
            return lock;
        } catch (InterruptedException exception) {
            outcome = "interrupted";
            Thread.currentThread().interrupt();
            throw new RedisLockUnavailableException(exception);
        } catch (RedisLockBusyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RedisLockUnavailableException(exception);
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

    private void releaseLock(
            Lock lock,
            String lockKey,
            long acquiredAt
    ) {
        try {
            meterRegistry.timer("redis.lock.hold")
                    .record(System.nanoTime() - acquiredAt, TimeUnit.NANOSECONDS);
        } catch (RuntimeException exception) {
            log.warn("Redis 락 보유 시간 계측 실패", exception);
        }

        try {
            lock.unlock();
        } catch (RuntimeException exception) {
            // 커밋된 요청을 실패 응답으로 바꾸지 않는다. 잔여 락은 TTL로 만료된다.
            log.error("Redis 락 해제 실패: lockKey={}", lockKey, exception);
        }
    }
}
