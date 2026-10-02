package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CollectionTradeFixture {

    public static CollectionTradeRequest createAcceptedRequestWithId(
            Long id, CollectionItem target, Member requester, CollectionItem offer, CollectionTradeType type
    ) {
        CollectionTradeRequest request = CollectionTradeRequest.create(target, requester, offer, type);
        ReflectionTestUtils.setField(request, "collectionTradeRequestId", id);
        request.accept();

        return request;
    }
}
