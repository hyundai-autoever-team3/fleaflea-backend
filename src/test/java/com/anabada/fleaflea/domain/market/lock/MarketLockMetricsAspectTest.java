package com.anabada.fleaflea.domain.market.lock;

import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketLockMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MarketLockMetricsAspect marketLockMetricsAspect = new MarketLockMetricsAspect(meterRegistry);

    @BeforeEach
    void setUp() {
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id meterId) {
                throw new IllegalStateException("metrics unavailable");
            }
        });
    }

    @Test
    @DisplayName("변경 실행 계측이 실패해도 정상 작업 결과를 반환한다")
    void measureChangeExecution_metricsFailure_preservesResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("updated");

        Object result = marketLockMetricsAspect.measureChangeExecution(joinPoint);

        assertThat(result).isEqualTo("updated");
    }

    @Test
    @DisplayName("조회 계측이 실패해도 원래 발생한 예외를 유지한다")
    void measureLockedLookup_metricsFailure_preservesOriginalException() throws Throwable {
        MarketNotFoundException marketNotFoundException = new MarketNotFoundException();
        when(joinPoint.proceed()).thenThrow(marketNotFoundException);

        assertThatThrownBy(() -> marketLockMetricsAspect.measureLockedLookup(joinPoint))
                .isSameAs(marketNotFoundException);
    }
}
