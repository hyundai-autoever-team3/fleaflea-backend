package com.anabada.fleaflea.domain.collection.repository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CollectionItemRepository
        extends JpaRepository<CollectionItem, Long>,
        CollectionItemRepositoryCustom {

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
