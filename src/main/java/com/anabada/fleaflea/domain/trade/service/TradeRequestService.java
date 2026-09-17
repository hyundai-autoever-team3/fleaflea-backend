package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.Trade;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.dto.request.TradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestCreateResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestDetailResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestStatusResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestSummaryResponse;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestAlreadyExistsException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestItemNotAvailableException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotFoundException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestSelfRequestException;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeRequestService {

    private static final List<TradeRequestStatus> LIST_STATUSES = List.of(
            TradeRequestStatus.PENDING,
            TradeRequestStatus.ACCEPTED,
            TradeRequestStatus.COMPLETED
    );

    private final TradeRequestRepository tradeRequestRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final TradeRepository tradeRepository;
    private final ImageService imageService;

    @Transactional
    public TradeRequestCreateResponse createTradeRequest(
            Long itemId,
            Long memberId,
            TradeRequestCreateRequest request
    ) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(ItemNotFoundException::new);

        validateMarketParticipant(
                item.getMarket().getMarketId(),
                memberId
        );

        if (item.getSeller().getMemberId().equals(memberId)) {
            throw new TradeRequestSelfRequestException();
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new TradeRequestItemNotAvailableException();
        }

        // 본인이 이미 요청한 상태(PENDING)에서 중복 요청 불가
        if (tradeRequestRepository
                .existsByItem_ItemIdAndRequester_MemberIdAndStatus(
                        itemId,
                        memberId,
                        TradeRequestStatus.PENDING
                )) {
            throw new TradeRequestAlreadyExistsException();
        }

        Member requester = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        TradeRequest tradeRequest = TradeRequest.create(
                item,
                requester,
                request.message(),
                request.rentalStartDate(),
                request.rentalEndDate()
        );

        return TradeRequestCreateResponse.from(
                tradeRequestRepository.save(tradeRequest)
        );
    }

    public TradeRequestDetailResponse getTradeRequest(
            Long requestId,
            Long memberId
    ) {
        TradeRequest tradeRequest = findTradeRequestOrThrow(requestId);

        tradeRequest.validateParticipant(memberId);

        return toTradeRequestDetailResponse(tradeRequest, memberId);
    }

    public PageResponse<TradeRequestSummaryResponse> getTradeRequests(
            Long memberId,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("tradeRequestId"))
        );

        return PageResponse.from(
                tradeRequestRepository.findAllByParticipantIdAndStatusIn(
                                memberId,
                                LIST_STATUSES,
                                pageable
                        )
                        .map(tradeRequest -> toTradeRequestSummaryResponse(
                                tradeRequest,
                                memberId
                        ))
        );
    }

    @Transactional
    public TradeRequestStatusResponse acceptTradeRequest(
            Long requestId,
            Long memberId
    ) {
        TradeRequest tradeRequest = findTradeRequestOrThrow(requestId);

        tradeRequest.accept(memberId);

        tradeRequestRepository.rejectOtherPendingRequests(
                tradeRequest.getItem().getItemId(),
                tradeRequest.getTradeRequestId(),
                TradeRequestStatus.PENDING,
                TradeRequestStatus.REJECTED
        );

        return TradeRequestStatusResponse.from(tradeRequest);
    }

    @Transactional
    public TradeRequestStatusResponse rejectTradeRequest(
            Long requestId,
            Long memberId
    ) {
        TradeRequest tradeRequest = findTradeRequestOrThrow(requestId);

        tradeRequest.reject(memberId);

        return TradeRequestStatusResponse.from(tradeRequest);
    }

    @Transactional
    public TradeRequestStatusResponse cancelTradeRequest(
            Long requestId,
            Long memberId
    ) {
        TradeRequest tradeRequest = findTradeRequestOrThrow(requestId);

        tradeRequest.cancel(memberId);

        return TradeRequestStatusResponse.from(tradeRequest);
    }
    @Transactional
    public TradeRequestStatusResponse confirmTradeRequestCompletion(
            Long requestId,
            Long memberId
    ) {
        TradeRequest tradeRequest = findTradeRequestOrThrow(requestId);

        tradeRequest.confirmCompletion(memberId);

        tradeRepository.save(
                Trade.create(tradeRequest)
        );

        return TradeRequestStatusResponse.from(tradeRequest);
    }

    private TradeRequest findTradeRequestOrThrow(Long requestId) {
        return tradeRequestRepository.findWithDetailsByTradeRequestId(requestId)
                .orElseThrow(TradeRequestNotFoundException::new);
    }

    private void validateMarketParticipant(Long marketId, Long memberId) {
        if (!marketMemberRepository
                .existsByMarket_MarketIdAndMember_MemberId(marketId, memberId)) {
            throw new MarketNotParticipantException();
        }
    }

    private TradeRequestSummaryResponse toTradeRequestSummaryResponse(
            TradeRequest tradeRequest,
            Long memberId
    ) {
        Item item = tradeRequest.getItem();

        return TradeRequestSummaryResponse.of(
                tradeRequest,
                memberId,
                imageService.getUrl(item.getImageKey())
        );
    }

    private TradeRequestDetailResponse toTradeRequestDetailResponse(
            TradeRequest tradeRequest,
            Long memberId
    ) {
        Item item = tradeRequest.getItem();
        Member counterparty = findCounterparty(tradeRequest, memberId);

        return TradeRequestDetailResponse.of(
                tradeRequest,
                memberId,
                imageService.getUrl(item.getImageKey()),
                imageService.getUrl(counterparty.getProfileImageKey())
        );
    }

    private Member findCounterparty(
            TradeRequest tradeRequest,
            Long memberId
    ) {
        if (tradeRequest.getRequester().getMemberId().equals(memberId)) {
            return tradeRequest.getItem().getSeller();
        }

        return tradeRequest.getRequester();
    }
}
