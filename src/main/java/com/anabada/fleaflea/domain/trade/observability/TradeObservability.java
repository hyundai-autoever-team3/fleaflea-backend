package com.anabada.fleaflea.domain.trade.observability;

import com.anabada.fleaflea.domain.trade.event.TradeKind;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeObservability {

    private final MeterRegistry meterRegistry;

    public void recordAfterCommit(String action, TradeKind kind) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("거래 성공 지표 등록 생략 - transaction synchronization 없음, action={}, kind={}",
                    action, kind.name().toLowerCase());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        meterRegistry.counter(
                                "fleaflea.trade.committed",
                                "action", action,
                                "kind", kind.name().toLowerCase()
                        ).increment();
                    }
                }
        );
    }
}
