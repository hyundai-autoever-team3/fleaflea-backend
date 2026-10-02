package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestHistoryDetailResponse;
import com.anabada.fleaflea.domain.trade.dto.TradeRequestListResponse;
import com.anabada.fleaflea.domain.trade.exception.InvalidTradeRequestDirectionException;
import com.anabada.fleaflea.domain.trade.exception.InvalidTradeRequestTypeException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestAccessDeniedException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotFoundException;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.global.image.ImageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeRequestListService {

    private final TradeRequestRepository tradeRequestRepository;
    private final CollectionTradeRequestRepository collectionTradeRequestRepository;
    private final BegRequestRepository begRequestRepository;
    private final TradeRepository tradeRepository;
    private final ImageService imageService;

    public List<TradeRequestListResponse> getTradeRequests(Long memberId, String direction) {
        boolean received;
        if ("received".equalsIgnoreCase(direction)) {
            received = true;
        } else if ("sent".equalsIgnoreCase(direction)) {
            received = false;
        } else {
            throw new InvalidTradeRequestDirectionException();
        }

        List<TradeRequest> items = received
                ? tradeRequestRepository.findByItem_Seller_MemberIdOrderByCreatedAtDesc(memberId)
                : tradeRequestRepository.findByRequester_MemberIdOrderByCreatedAtDesc(memberId);
        List<CollectionTradeRequest> collections = received
                ? collectionTradeRequestRepository.findByOwner_MemberIdOrderByCreatedAtDesc(memberId)
                : collectionTradeRequestRepository.findByRequester_MemberIdOrderByCreatedAtDesc(memberId);
        List<BegRequest> begs = received
                ? begRequestRepository.findByOwner_MemberIdOrderByCreatedAtDesc(memberId)
                : begRequestRepository.findByApplicant_MemberIdOrderByCreatedAtDesc(memberId);

        List<TradeRequestListResponse> result = new ArrayList<>(items.size() + collections.size() + begs.size());
        for (TradeRequest request : items) {
            result.add(TradeRequestListResponse.from(request, toMemberSummaryResponse(request.getItem().getSeller()),
                    toMemberSummaryResponse(request.getRequester()), imageService.getUrl(request.getItem().getImageKey())));
        }
        for (CollectionTradeRequest request : collections) {
            String imageKey = request.getCollectionItem() == null ? null : request.getCollectionItem().getImageKey();
            result.add(TradeRequestListResponse.from(request, toMemberSummaryResponse(request.getOwner()),
                    toMemberSummaryResponse(request.getRequester()), imageService.getUrl(imageKey)));
        }
        for (BegRequest request : begs) {
            String imageKey = request.getCollectionItem() == null ? null : request.getCollectionItem().getImageKey();
            result.add(TradeRequestListResponse.from(request, toMemberSummaryResponse(request.getOwner()),
                    toMemberSummaryResponse(request.getApplicant()), imageService.getUrl(imageKey)));
        }

        result.sort(Comparator.comparing(TradeRequestListResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    public TradeRequestHistoryDetailResponse getTradeRequestHistoryDetail(
            Long memberId,
            String requestType,
            Long requestId
    ) {
        if (requestType == null) {
            throw new InvalidTradeRequestTypeException();
        }

        return switch (requestType.toUpperCase(Locale.ROOT)) {
            case "ITEM" -> itemDetail(memberId, requestId);
            case "COLLECTION" -> collectionDetail(memberId, requestId);
            case "BEG" -> begDetail(memberId, requestId);
            default -> throw new InvalidTradeRequestTypeException();
        };
    }

    private TradeRequestHistoryDetailResponse itemDetail(
            Long memberId,
            Long requestId
    ) {
        TradeRequest request = tradeRequestRepository
                .findWithDetailsByTradeRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
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
                imageService.getUrl(item.getImageKey()),
                item.getTradeType().name(),
                item.getPrice(),
                request.getStatus(),
                toMemberSummaryResponse(item.getSeller()),
                toMemberSummaryResponse(request.getRequester()),
                null,
                request.getMessage(),
                request.getRentalStartDate(),
                request.getRentalEndDate(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                tradeRepository.findByTradeRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private TradeRequestHistoryDetailResponse collectionDetail(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest request = collectionTradeRequestRepository
                .findWithDetailsByCollectionTradeRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
        CollectionItem target = request.getCollectionItem();
        requireParty(
                memberId,
                request.getOwner().getMemberId(),
                request.getRequester().getMemberId()
        );

        CollectionItem offer = request.getOfferCollectionItem();
        TradeRequestHistoryDetailResponse.OfferItem offerResponse =
                request.getOfferCollectionItemSnapshotId() == null ? null
                        : new TradeRequestHistoryDetailResponse.OfferItem(
                                request.getOfferCollectionItemSnapshotId(),
                                request.getOfferCollectionItemTitle(),
                                request.getOfferCollectionItemDescription(),
                                imageService.getUrl(offer == null ? null : offer.getImageKey())
                        );

        return new TradeRequestHistoryDetailResponse(
                "COLLECTION",
                request.getCollectionTradeRequestId(),
                request.getCollectionItemSnapshotId(),
                request.getCollectionItemTitle(),
                request.getCollectionItemDescription(),
                imageService.getUrl(target == null ? null : target.getImageKey()),
                request.getTradeType().name(),
                null,
                request.getStatus(),
                toMemberSummaryResponse(request.getOwner()),
                toMemberSummaryResponse(request.getRequester()),
                offerResponse,
                null,
                null,
                null,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                tradeRepository.findByCollectionTradeRequestId(requestId)
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
                .orElseThrow(TradeRequestNotFoundException::new);
        CollectionItem target = request.getCollectionItem();
        requireParty(
                memberId,
                request.getOwner().getMemberId(),
                request.getApplicant().getMemberId()
        );

        return new TradeRequestHistoryDetailResponse(
                "BEG",
                request.getBegRequestId(),
                request.getCollectionItemSnapshotId(),
                request.getCollectionItemTitle(),
                request.getCollectionItemDescription(),
                imageService.getUrl(target == null ? null : target.getImageKey()),
                null,
                null,
                request.getStatus().toTradeRequestStatus(),
                toMemberSummaryResponse(request.getOwner()),
                toMemberSummaryResponse(request.getApplicant()),
                null,
                request.getStory(),
                null,
                null,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                tradeRepository.findByBegRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private void requireParty(Long memberId, Long ownerId, Long requesterId) {
        if (!memberId.equals(ownerId) && !memberId.equals(requesterId)) {
            throw new TradeRequestAccessDeniedException();
        }
    }

    private MemberSummaryResponse toMemberSummaryResponse(Member member) {
        return MemberSummaryResponse.from(member, imageService.getUrl(member.getProfileImageKey()));
    }
}
