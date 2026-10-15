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

    private static final String COLLECTION_ITEM_LOCK_PREFIX = "collection-item:";

    private final CollectionTradeRequestRepository collectionTradeRequestRepository;
    private final BegRequestRepository begRequestRepository;

    public List<String> forCreation(
            Long collectionItemId,
            CollectionTradeRequestCreateRequest collectionTradeRequestCreateRequest
    ) {
        List<String> lockKeys = new ArrayList<>();
        lockKeys.add(createCollectionItemLockKey(collectionItemId));
        if (collectionTradeRequestCreateRequest.tradeType() == CollectionTradeType.EXCHANGE
                && collectionTradeRequestCreateRequest.offerCollectionItemId() != null) {
            lockKeys.add(createCollectionItemLockKey(collectionTradeRequestCreateRequest.offerCollectionItemId()));
        }
        return lockKeys;
    }

    public List<String> forTrade(Long collectionTradeRequestId) {
        Long targetCollectionItemId = collectionTradeRequestRepository.findTargetItemId(collectionTradeRequestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
        List<String> lockKeys = new ArrayList<>();
        lockKeys.add(createCollectionItemLockKey(targetCollectionItemId));
        collectionTradeRequestRepository.findOfferItemId(collectionTradeRequestId)
                .ifPresent(offerCollectionItemId -> lockKeys.add(createCollectionItemLockKey(offerCollectionItemId)));
        return lockKeys;
    }

    public String forBeg(Long begRequestId) {
        Long targetCollectionItemId = begRequestRepository.findTargetItemId(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);
        return createCollectionItemLockKey(targetCollectionItemId);
    }

    public List<String> forItem(Long collectionItemId) {
        return collectionItemId == null ? List.of() : List.of(createCollectionItemLockKey(collectionItemId));
    }

    private String createCollectionItemLockKey(Long collectionItemId) {
        return COLLECTION_ITEM_LOCK_PREFIX + collectionItemId;
    }
}
