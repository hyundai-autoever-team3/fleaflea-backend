package com.anabada.fleaflea.domain.collectionitem.lock;

import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotFoundException;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeRequestNotFoundException;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("collectionLockKeys")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionLockKeys {
    private final CollectionTradeRequestRepository tradeRepository;
    private final BegRequestRepository begRepository;

    public List<String> forCreation(Long itemId, CollectionTradeRequestCreateRequest request) {
        List<String> keys = new ArrayList<>();
        keys.add(key(itemId));
        if (request.tradeType() == CollectionTradeType.EXCHANGE && request.offerCollectionItemId() != null) {
            keys.add(key(request.offerCollectionItemId()));
        }
        return keys;
    }

    public List<String> forTrade(Long requestId) {
        Long targetId = tradeRepository.findTargetItemId(requestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
        List<String> keys = new ArrayList<>();
        keys.add(key(targetId));
        tradeRepository.findOfferItemId(requestId).ifPresent(id -> keys.add(key(id)));
        return keys;
    }

    public String forBeg(Long requestId) {
        return key(begRepository.findTargetItemId(requestId).orElseThrow(BegRequestNotFoundException::new));
    }

    public List<String> forItem(Long itemId) {
        return itemId == null ? List.of() : List.of(key(itemId));
    }

    private String key(Long itemId) {
        return "collection-item:" + itemId;
    }
}
