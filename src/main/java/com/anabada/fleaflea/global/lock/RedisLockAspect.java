package com.anabada.fleaflea.global.lock;

import com.anabada.fleaflea.global.lock.exception.RedisLockBusyException;
import com.anabada.fleaflea.global.lock.exception.RedisLockUnavailableException;
import com.anabada.fleaflea.global.exception.BusinessException;
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
    private final Duration waitTime;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public RedisLockAspect(
            RedisLockRegistry redisLockRegistry,
            BeanFactory beanFactory,
            @Value("${app.redis-lock.wait-time:5s}") Duration waitTime
    ) {
        if (waitTime.isNegative()) {
            throw new IllegalArgumentException("Redis lock wait time must not be negative");
        }
        this.redisLockRegistry = redisLockRegistry;
        this.beanFactory = beanFactory;
        this.waitTime = waitTime;
    }

    @Around("@annotation(redisLocked)")
    public Object executeWithLock(
            ProceedingJoinPoint joinPoint,
            RedisLocked redisLocked
    ) throws Throwable {
        String lockKey = getLockKey(joinPoint, redisLocked);
        Lock lock = acquireLock(lockKey);
        boolean releaseAfterTransaction = false;

        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseLock(lock, lockKey);
                    }
                });
                releaseAfterTransaction = true;
            }
            return joinPoint.proceed();
        } finally {
            if (!releaseAfterTransaction) {
                releaseLock(lock, lockKey);
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
        Lock lock;
        boolean acquired;
        try {
            lock = redisLockRegistry.obtain(lockKey);
            acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RedisLockUnavailableException(exception);
        } catch (RuntimeException exception) {
            throw new RedisLockUnavailableException(exception);
        }

        if (!acquired) {
            throw new RedisLockBusyException();
        }
        return lock;
    }

    private void releaseLock(
            Lock lock,
            String lockKey
    ) {
        try {
            lock.unlock();
        } catch (RuntimeException exception) {
            // 커밋된 요청을 실패 응답으로 바꾸지 않는다. 잔여 락은 TTL로 만료된다.
            log.error("Redis 락 해제 실패: lockKey={}", lockKey, exception);
        }
    }
}
