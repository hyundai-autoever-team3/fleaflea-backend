package com.anabada.fleaflea.domain.collectionitem.repository;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionItemRepository
        extends JpaRepository<CollectionItem, Long>,
        CollectionItemRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select collectionItem from CollectionItem collectionItem join fetch collectionItem.owner where collectionItem.collectionItemId = :id")
    Optional<CollectionItem> findLockedById(@Param("id") Long collectionItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select collectionItem
            from CollectionItem collectionItem
            join fetch collectionItem.owner
            where collectionItem.collectionItemId in :ids
            order by collectionItem.collectionItemId
            """)
    List<CollectionItem> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
