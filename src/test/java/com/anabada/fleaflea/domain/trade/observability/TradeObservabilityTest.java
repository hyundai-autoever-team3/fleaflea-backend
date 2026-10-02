package com.anabada.fleaflea.domain.trade.observability;

import com.anabada.fleaflea.domain.trade.event.TradeKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class TradeObservabilityTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void successfulTradeIsCountedOnlyAfterCommit() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TradeObservability observability = new TradeObservability(meterRegistry);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        observability.recordAfterCommit("accepted", TradeKind.ITEM);

        assertThat(committedCount(meterRegistry)).isZero();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        assertThat(committedCount(meterRegistry)).isEqualTo(1);
    }

    @Test
    void rolledBackTradeIsNotCounted() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TradeObservability observability = new TradeObservability(meterRegistry);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        observability.recordAfterCommit("accepted", TradeKind.ITEM);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(1));

        assertThat(committedCount(meterRegistry)).isZero();
    }

    private double committedCount(SimpleMeterRegistry meterRegistry) {
        return meterRegistry.counter(
                "fleaflea.trade.committed",
                "action", "accepted",
                "kind", "item"
        ).count();
    }
}
