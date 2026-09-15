package com.anabada.fleaflea.domain.collectionitem.repository;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionItemRepository
        extends JpaRepository<CollectionItem, Long> {

    @EntityGraph(attributePaths = {"owner"})
    Page<CollectionItem> findAllByOwner(
            Member owner,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"owner"})
    Page<CollectionItem> findAllByOwnerAndIsPublicTrue(
            Member owner,
            Pageable pageable
    );
}