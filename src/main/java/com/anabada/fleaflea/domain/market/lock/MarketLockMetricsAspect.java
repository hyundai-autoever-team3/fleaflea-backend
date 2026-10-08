package com.anabada.fleaflea.domain.market.lock;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MarketLockMetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("execution(* com.anabada.fleaflea.domain.market.repository.MarketRepository.findLockedById(..))")
    public Object measureLockedLookup(ProceedingJoinPoint joinPoint) throws Throwable {
        return measure(joinPoint, "market.locked.lookup");
    }

    @Around("@annotation(com.anabada.fleaflea.global.lock.RedisLocked) && "
            + "(within(com.anabada.fleaflea.domain.market.service..*) || "
            + "within(com.anabada.fleaflea.domain.marketmember.service..*))")
    public Object measureChangeExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        return measure(joinPoint, "market.change.execution");
    }

    private Object measure(
            ProceedingJoinPoint joinPoint,
            String metricName
    ) throws Throwable {
        long startedAt = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            try {
                meterRegistry.timer(metricName)
                        .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            } catch (RuntimeException exception) {
                log.warn("마켓 처리 시간 계측 실패: metricName={}", metricName, exception);
            }
        }
    }
}
