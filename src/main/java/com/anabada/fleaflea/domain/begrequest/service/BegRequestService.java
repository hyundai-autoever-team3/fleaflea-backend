package com.anabada.fleaflea.domain.begrequest.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingDetailResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingRequest;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingStatusResponse;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestAlreadyCompletedException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestAlreadyExistsException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotAcceptedException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotApplicantException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotFoundException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotOwnerException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestNotPendingException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestSelfItemException;
import com.anabada.fleaflea.domain.begrequest.exception.CollectionItemNotPublicException;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.Trade;
import com.anabada.fleaflea.domain.trade.event.TradeAcceptedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCancelledEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeDealType;
import com.anabada.fleaflea.domain.trade.event.TradeKind;
import com.anabada.fleaflea.domain.trade.event.TradeRejectedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeRequestedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeTarget;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BegRequestService {

    private final BegRequestRepository begRequestRepository;
    private final MemberRepository memberRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BeggingResponse createBegging(
            Long memberId,
            Long collectionItemId,
            BeggingRequest request
            ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        CollectionItem collectionItem = collectionItemRepository.findById(collectionItemId)
                .orElseThrow(CollectionItemNotFoundException::new);

        if (memberId.equals(collectionItem.getOwner().getMemberId())) {
            throw new BegRequestSelfItemException();
        }

        if (!collectionItem.getIsPublic()) {
            throw new CollectionItemNotPublicException();
        }

        if (begRequestRepository.existsByApplicantAndCollectionItemAndStatus(
                member,
                collectionItem,
                BegRequestStatus.PENDING
        )) {
            throw new BegRequestAlreadyExistsException();
        }

        BegRequest begRequest = BegRequest.create(
                collectionItem,
                member,
                request.story(),
                BegRequestStatus.PENDING
        );
        begRequestRepository.save(begRequest);

        eventPublisher.publishEvent(
                TradeRequestedEvent.of(
                        begRequest.getBegRequestId(),
                        member.getMemberId(),
                        collectionItem.getOwner().getMemberId(),
                        member.getNickname(),
                        toTradeTarget(collectionItem)
                )
        );

        return BeggingResponse.from(
                begRequest
        );
    }

    @Transactional(readOnly = true)
    public BeggingDetailResponse getBeggingDetails(
            Long memberId,
            Long begRequestId
    ) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        Long ownerId = begRequest.getOwner().getMemberId();
        Long applicantId = begRequest.getApplicant().getMemberId();

        if (!memberId.equals(ownerId) && !memberId.equals(applicantId)) {
            throw new BegRequestNotOwnerException();
        }

        return BeggingDetailResponse.from(begRequest);
    }

    @Transactional
    public BeggingStatusResponse acceptBeggingRequest(Long memberId , Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getOwner().getMemberId().equals(memberId)) {
            throw new BegRequestNotOwnerException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }


        begRequest.accept();

        Member owner = begRequest.getOwner();

        eventPublisher.publishEvent(
                TradeAcceptedEvent.of(
                        begRequest.getBegRequestId(),
                        begRequest.getApplicant()
                                .getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(
                                begRequest.getCollectionItem()
                        )
                )
        );

        return BeggingStatusResponse.from(begRequest);
    }

    @Transactional
    public BeggingStatusResponse rejectBeggingRequest(Long memberId ,Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getOwner().getMemberId().equals(memberId)) {
            throw new BegRequestNotOwnerException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }


        begRequest.reject();

        Member owner = begRequest.getOwner();

        eventPublisher.publishEvent(
                TradeRejectedEvent.of(
                        begRequest.getBegRequestId(),
                        begRequest.getApplicant()
                                .getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(
                                begRequest.getCollectionItem()
                        )
                )
        );

        return BeggingStatusResponse.from(begRequest);
    }

    @Transactional
    public BeggingStatusResponse cancelBeggingRequest(Long memberId, Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getApplicant().getMemberId().equals(memberId)) {
            throw new BegRequestNotApplicantException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }

        begRequest.cancel();

        eventPublisher.publishEvent(
                TradeCancelledEvent.of(
                        begRequest.getBegRequestId(),
                        begRequest.getApplicant()
                                .getMemberId(),
                        begRequest.getOwner()
                                .getMemberId(),
                        begRequest.getApplicant()
                                .getNickname(),
                        toTradeTarget(
                                begRequest.getCollectionItem()
                        )
                )
        );

        return BeggingStatusResponse.from(begRequest);
    }

    @Transactional
    public BeggingStatusResponse completeBeggingRequest(
            Long memberId,
            Long begRequestId
    ) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        Member applicant = begRequest.getApplicant();
        Member owner = begRequest.getOwner();

        if (!applicant.getMemberId().equals(memberId)) {
            throw new BegRequestNotApplicantException();
        }

        if (begRequest.getStatus() != BegRequestStatus.ACCEPTED) {
            throw new BegRequestNotAcceptedException();
        }

        if (tradeRepository.existsByBegRequestId(begRequestId)) {
            throw new BegRequestAlreadyCompletedException();
        }

        CollectionItem collectionItem = begRequest.getCollectionItem();

        collectionItem.transferTo(applicant);
        begRequest.complete();

        tradeRepository.save(
                Trade.ofBegRequest(
                        begRequestId,
                        applicant.getMemberId(),
                        owner.getMemberId()
                )
        );

        eventPublisher.publishEvent(
                TradeCompletedEvent.of(
                        begRequest.getBegRequestId(),
                        applicant.getMemberId(),
                        owner.getMemberId(),
                        applicant.getNickname(),
                        toTradeTarget(
                                begRequest.getCollectionItem()
                        )
                )
        );

        return BeggingStatusResponse.from(begRequest);
    }


    private TradeTarget toTradeTarget(CollectionItem collectionItem) {
        return TradeTarget.of(
                TradeKind.BEG,
                TradeDealType.BEG,
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle()
        );
    }
}