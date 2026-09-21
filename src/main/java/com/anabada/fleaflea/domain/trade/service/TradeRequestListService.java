package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestHistoryDetailResponse;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestListResponse;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeRequestListService {
    private final TradeRequestRepository itemRequests;
    private final CollectionTradeRequestRepository collectionRequests;
    private final BegRequestRepository begRequestRepository;
    private final TradeRepository trades;
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

    public TradeRequestHistoryDetailResponse detail(
            Long memberId,
            String requestType,
            Long requestId
    ) {
        return switch (requestType.toUpperCase(Locale.ROOT)) {
            case "ITEM" -> itemDetail(memberId, requestId);
            case "COLLECTION" -> collectionDetail(memberId, requestId);
            case "BEG" -> begDetail(memberId, requestId);
            default -> throw new BusinessException(
                    ErrorCode.TRADE_REQUEST_INVALID_TYPE
            );
        };
    }

    private TradeRequestHistoryDetailResponse itemDetail(
            Long memberId,
            Long requestId
    ) {
        TradeRequest request = itemRequests
                .findWithDetailsByTradeRequestId(requestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRADE_REQUEST_NOT_FOUND
                ));
        Item item = request.getItem();
        requireParty(
                memberId,
                item.getSeller().getMemberId(),
                request.getRequester().getMemberId()
        );

        return new TradeRequestHistoryDetailResponse(
                "ITEM",
                request.getTradeRequestId(),
                item.getItemId(),
                item.getTitle(),
                item.getDescription(),
                images.getUrl(item.getImageKey()),
                item.getTradeType().name(),
                item.getPrice(),
                request.getStatus(),
                member(item.getSeller()),
                member(request.getRequester()),
                null,
                request.getMessage(),
                request.getRentalStartDate(),
                request.getRentalEndDate(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                trades.findByTradeRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private TradeRequestHistoryDetailResponse collectionDetail(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest request = collectionRequests
                .findWithDetailsByCollectionTradeRequestId(requestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRADE_REQUEST_NOT_FOUND
                ));
        CollectionItem target = request.getCollectionItem();
        requireParty(
                memberId,
                request.getOwner().getMemberId(),
                request.getRequester().getMemberId()
        );

        CollectionItem offer = request.getOfferCollectionItem();
        TradeRequestHistoryDetailResponse.OfferItem offerResponse =
                offer == null ? null : new TradeRequestHistoryDetailResponse.OfferItem(
                        offer.getCollectionItemId(),
                        offer.getTitle(),
                        offer.getDescription(),
                        images.getUrl(offer.getImageKey())
                );

        return new TradeRequestHistoryDetailResponse(
                "COLLECTION",
                request.getCollectionTradeRequestId(),
                target.getCollectionItemId(),
                target.getTitle(),
                target.getDescription(),
                images.getUrl(target.getImageKey()),
                request.getTradeType().name(),
                null,
                request.getStatus(),
                member(request.getOwner()),
                member(request.getRequester()),
                offerResponse,
                null,
                null,
                null,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                trades.findByCollectionTradeRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private TradeRequestHistoryDetailResponse begDetail(
            Long memberId,
            Long requestId
    ) {
        BegRequest request = begRequestRepository
                .findWithDetailsByBegRequestId(requestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRADE_REQUEST_NOT_FOUND
                ));
        CollectionItem target = request.getCollectionItem();
        requireParty(
                memberId,
                request.getOwner().getMemberId(),
                request.getApplicant().getMemberId()
        );

        return new TradeRequestHistoryDetailResponse(
                "BEG",
                request.getBegRequestId(),
                target.getCollectionItemId(),
                target.getTitle(),
                target.getDescription(),
                images.getUrl(target.getImageKey()),
                null,
                null,
                request.getStatus().toTradeRequestStatus(),
                member(request.getOwner()),
                member(request.getApplicant()),
                null,
                request.getStory(),
                null,
                null,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                trades.findByBegRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private void requireParty(Long memberId, Long ownerId, Long requesterId) {
        if (!memberId.equals(ownerId) && !memberId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.TRADE_REQUEST_ACCESS_DENIED);
        }
    }

    private MemberSummaryResponse member(Member member) {
        return MemberSummaryResponse.from(member, images.getUrl(member.getProfileImageKey()));
    }
}
