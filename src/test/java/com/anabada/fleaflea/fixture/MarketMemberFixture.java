package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;

public class MarketMemberFixture {

    public static MarketMember createMarketMember(Market market, Member member) {
        return MarketMember.create(market, member);
    }
}