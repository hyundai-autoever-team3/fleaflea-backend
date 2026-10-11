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

    public List<TradeRequestListResponse> getTradeRequests(
            Long memberId,
            String direction
    ) {
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
        for (TradeRequest tradeRequest : items) {
            result.add(TradeRequestListResponse.from(tradeRequest, toMemberSummaryResponse(tradeRequest.getItem().getSeller()),
                    toMemberSummaryResponse(tradeRequest.getRequester()), imageService.getUrl(tradeRequest.getItem().getImageKey())));
        }
        for (CollectionTradeRequest collectionTradeRequest : collections) {
            String imageKey = collectionTradeRequest.getCollectionItem() == null
                    ? null
                    : collectionTradeRequest.getCollectionItem().getImageKey();
            result.add(TradeRequestListResponse.from(collectionTradeRequest, toMemberSummaryResponse(collectionTradeRequest.getOwner()),
                    toMemberSummaryResponse(collectionTradeRequest.getRequester()), imageService.getUrl(imageKey)));
        }
        for (BegRequest begRequest : begs) {
            String imageKey = begRequest.getCollectionItem() == null ? null : begRequest.getCollectionItem().getImageKey();
            result.add(TradeRequestListResponse.from(begRequest, toMemberSummaryResponse(begRequest.getOwner()),
                    toMemberSummaryResponse(begRequest.getApplicant()), imageService.getUrl(imageKey)));
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
            case "ITEM" -> getItemTradeRequestDetail(memberId, requestId);
            case "COLLECTION" -> getCollectionTradeRequestDetail(memberId, requestId);
            case "BEG" -> getBegRequestDetail(memberId, requestId);
            default -> throw new InvalidTradeRequestTypeException();
        };
    }

    private TradeRequestHistoryDetailResponse getItemTradeRequestDetail(
            Long memberId,
            Long requestId
    ) {
        TradeRequest tradeRequest = tradeRequestRepository
                .findWithDetailsByTradeRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
        Item item = tradeRequest.getItem();
        validateTradeParticipant(
                memberId,
                item.getSeller().getMemberId(),
                tradeRequest.getRequester().getMemberId()
        );

        return TradeRequestHistoryDetailResponse.from(
                tradeRequest,
                toMemberSummaryResponse(item.getSeller()),
                toMemberSummaryResponse(tradeRequest.getRequester()),
                imageService.getUrl(item.getImageKey()),
                tradeRepository.findByTradeRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private TradeRequestHistoryDetailResponse getCollectionTradeRequestDetail(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest collectionTradeRequest = collectionTradeRequestRepository
                .findWithDetailsByCollectionTradeRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
        CollectionItem target = collectionTradeRequest.getCollectionItem();
        validateTradeParticipant(
                memberId,
                collectionTradeRequest.getOwner().getMemberId(),
                collectionTradeRequest.getRequester().getMemberId()
        );

        CollectionItem offer = collectionTradeRequest.getOfferCollectionItem();
        TradeRequestHistoryDetailResponse.OfferItem offerResponse = TradeRequestHistoryDetailResponse.OfferItem.from(
                collectionTradeRequest,
                imageService.getUrl(offer == null ? null : offer.getImageKey())
        );

        return TradeRequestHistoryDetailResponse.from(
                collectionTradeRequest,
                toMemberSummaryResponse(collectionTradeRequest.getOwner()),
                toMemberSummaryResponse(collectionTradeRequest.getRequester()),
                imageService.getUrl(target == null ? null : target.getImageKey()),
                offerResponse,
                tradeRepository.findByCollectionTradeRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private TradeRequestHistoryDetailResponse getBegRequestDetail(
            Long memberId,
            Long requestId
    ) {
        BegRequest begRequest = begRequestRepository
                .findWithDetailsByBegRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
        CollectionItem target = begRequest.getCollectionItem();
        validateTradeParticipant(
                memberId,
                begRequest.getOwner().getMemberId(),
                begRequest.getApplicant().getMemberId()
        );

        return TradeRequestHistoryDetailResponse.from(
                begRequest,
                toMemberSummaryResponse(begRequest.getOwner()),
                toMemberSummaryResponse(begRequest.getApplicant()),
                imageService.getUrl(target == null ? null : target.getImageKey()),
                tradeRepository.findByBegRequestId(requestId)
                        .map(trade -> trade.getCompletedAt())
                        .orElse(null)
        );
    }

    private void validateTradeParticipant(
            Long memberId,
            Long ownerId,
            Long requesterId
    ) {
        if (!memberId.equals(ownerId) && !memberId.equals(requesterId)) {
            throw new TradeRequestAccessDeniedException();
        }
    }

    private MemberSummaryResponse toMemberSummaryResponse(Member member) {
        return MemberSummaryResponse.from(member, imageService.getUrl(member.getProfileImageKey()));
    }
}
