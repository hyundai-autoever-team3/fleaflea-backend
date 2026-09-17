package com.anabada.fleaflea.domain.begrequest.repository;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BegRequestRepository extends JpaRepository<BegRequest, Long> {
    boolean existsByApplicantAndCollectionItemAndStatus(
            Member applicant,
            CollectionItem collectionItem,
            BegRequestStatus status
            );
}


