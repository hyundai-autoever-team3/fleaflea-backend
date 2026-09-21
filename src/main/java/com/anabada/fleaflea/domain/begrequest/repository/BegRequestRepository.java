package com.anabada.fleaflea.domain.begrequest.repository;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BegRequestRepository extends JpaRepository<BegRequest, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from BegRequest request
            where request.collectionItem.collectionItemId = :collectionItemId
              and request.status in :statuses
            """)
    void deleteAllByCollectionItemIdAndStatusIn(
            @Param("collectionItemId") Long collectionItemId,
            @Param("statuses") Collection<BegRequestStatus> statuses
    );

    boolean existsByApplicantAndCollectionItemAndStatus(
            Member applicant,
            CollectionItem collectionItem,
            BegRequestStatus status
            );

    boolean existsByCollectionItem_CollectionItemIdAndStatus(
            Long collectionItemId,
            BegRequestStatus status
    );

    List<BegRequest> findByOwner_MemberIdOrderByCreatedAtDesc(Long memberId);

    List<BegRequest> findByApplicant_MemberIdOrderByCreatedAtDesc(
            Long memberId
    );
}
