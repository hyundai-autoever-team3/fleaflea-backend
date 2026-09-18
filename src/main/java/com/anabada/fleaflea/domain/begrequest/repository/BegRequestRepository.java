package com.anabada.fleaflea.domain.begrequest.repository;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BegRequestRepository extends JpaRepository<BegRequest, Long> {
    boolean existsByApplicantAndCollectionItemAndStatus(
            Member applicant,
            CollectionItem collectionItem,
            BegRequestStatus status
            );
    List<BegRequest> findByCollectionItem_Owner_MemberIdOrderByCreatedAtDesc(
            Long memberId
    );

    List<BegRequest> findByApplicant_MemberIdOrderByCreatedAtDesc(
            Long memberId
    );
}


