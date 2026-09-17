package com.anabada.fleaflea.domain.begrequest.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingDetailResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingRequest;
import com.anabada.fleaflea.domain.begrequest.exception.*;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BegRequestService {
    private final BegRequestRepository begRequestRepository;
    private final MemberRepository memberRepository;
    private final CollectionItemRepository collectionItemRepository;

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

        Long ownerId = begRequest.getCollectionItem().getOwner().getMemberId();
        Long applicantId = begRequest.getApplicant().getMemberId();

        if (!memberId.equals(ownerId) && !memberId.equals(applicantId)) {
            throw new BegRequestNotOwnerException();
        }

        return BeggingDetailResponse.from(begRequest);
    }

    @Transactional
    public void acceptBeggingRequest(Long memberId ,Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getCollectionItem().getOwner().getMemberId().equals(memberId)) {
            throw new BegRequestNotOwnerException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }


        begRequest.accept();
    }

    @Transactional
    public void rejectBeggingRequest(Long memberId ,Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getCollectionItem().getOwner().getMemberId().equals(memberId)) {
            throw new BegRequestNotOwnerException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }


        begRequest.reject();
    }

    @Transactional
    public void cancelBeggingRequest(Long memberId, Long begRequestId) {
        BegRequest begRequest = begRequestRepository.findById(begRequestId)
                .orElseThrow(BegRequestNotFoundException::new);

        if (!begRequest.getApplicant().getMemberId().equals(memberId)) {
            throw new BegRequestNotApplicantException();
        }

        if (begRequest.getStatus() != BegRequestStatus.PENDING) {
            throw new BegRequestNotPendingException();
        }



        begRequest.cancel();
    }



}
