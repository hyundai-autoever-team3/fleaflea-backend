package com.anabada.fleaflea.domain.begrequest.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingResponse;
import com.anabada.fleaflea.domain.begrequest.dto.CreateBeggingRequest;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestAlreadyExistsException;
import com.anabada.fleaflea.domain.begrequest.exception.BegRequestSelfItemException;
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
            CreateBeggingRequest request
            ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        CollectionItem collectionItem = collectionItemRepository.findById(collectionItemId)
                .orElseThrow(CollectionItemNotFoundException::new);

        if (memberId.equals(collectionItem.getOwner().getMemberId())) {
            throw new BegRequestSelfItemException();
        }

        if (begRequestRepository.existsByApplicantAndCollectionItem(member, collectionItem)) {
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

}
