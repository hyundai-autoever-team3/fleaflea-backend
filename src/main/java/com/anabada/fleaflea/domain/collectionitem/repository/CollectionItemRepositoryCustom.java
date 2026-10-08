package com.anabada.fleaflea.domain.collectionitem.repository;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CollectionItemRepositoryCustom {

    Page<CollectionItem> search(
            Long ownerId,
            boolean publicOnly,
            CollectionItemSearchCondition condition,
            Pageable pageable
    );
}
