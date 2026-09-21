package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestListResponse;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeRequestListService {
    private final TradeRequestRepository itemRequests;
    private final CollectionTradeRequestRepository collectionRequests;
    private final BegRequestRepository begRequestRepository;
    private final ImageService images;

    public List<TradeRequestListResponse> list(Long memberId, String direction) {
        boolean received;
        if ("received".equalsIgnoreCase(direction)) received = true;
        else if ("sent".equalsIgnoreCase(direction)) received = false;
        else throw new BusinessException(ErrorCode.TRADE_REQUEST_INVALID_DIRECTION);

        List<TradeRequest> items = received
                ? itemRequests.findByItem_Seller_MemberIdOrderByCreatedAtDesc(memberId)
                : itemRequests.findByRequester_MemberIdOrderByCreatedAtDesc(memberId);
        List<CollectionTradeRequest> collections = received
                ? collectionRequests.findByOwner_MemberIdOrderByCreatedAtDesc(memberId)
                : collectionRequests.findByRequester_MemberIdOrderByCreatedAtDesc(memberId);
        List<BegRequest> begs = received
                ? begRequestRepository.findByOwner_MemberIdOrderByCreatedAtDesc(memberId)
                : begRequestRepository.findByApplicant_MemberIdOrderByCreatedAtDesc(memberId);

        List<TradeRequestListResponse> result = new ArrayList<>(items.size() + collections.size() + begs.size());
        for (TradeRequest request : items) {
            result.add(new TradeRequestListResponse(
                    "ITEM", request.getTradeRequestId(), request.getItem().getItemId(),
                    request.getItem().getTitle(), request.getItem().getTradeType().name(),
                    request.getStatus(), member(request.getItem().getSeller()),
                    member(request.getRequester()),
                    images.getUrl(request.getItem().getImageKey()),
                    request.getCreatedAt()));
        }
        for (CollectionTradeRequest request : collections) {
            result.add(new TradeRequestListResponse(
                    "COLLECTION", request.getCollectionTradeRequestId(),
                    request.getCollectionItem().getCollectionItemId(),
                    request.getCollectionItem().getTitle(), request.getTradeType().name(),
                    request.getStatus(), member(request.getOwner()),
                    member(request.getRequester()),
                    images.getUrl(request.getCollectionItem().getImageKey()),
                    request.getCreatedAt()));
        }

        for (BegRequest request : begs) {
            result.add(new TradeRequestListResponse(
                    "BEG",
                    request.getBegRequestId(),
                    request.getCollectionItem().getCollectionItemId(),
                    request.getCollectionItem().getTitle(),
                    null,
                    request.getStatus().toTradeRequestStatus(),
                    member(request.getOwner()),
                    member(request.getApplicant()),
                    images.getUrl(request.getCollectionItem().getImageKey()),
                    request.getCreatedAt()
            ));
        }
        result.sort(Comparator.comparing(TradeRequestListResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private MemberSummaryResponse member(Member member) {
        return MemberSummaryResponse.from(member, images.getUrl(member.getProfileImageKey()));
    }
}
