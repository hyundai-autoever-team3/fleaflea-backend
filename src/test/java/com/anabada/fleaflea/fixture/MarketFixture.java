package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarketFixture {

    // 초대 코드는 유니크 제약이 있어 매번 다른 값으로 만든다.
    public static Market createMarket(Member host) {
        return Market.create(
                host,
                "test market",
                "test market description",
                null,
                "test-market-" + UUID.randomUUID()
        );
    }
}
