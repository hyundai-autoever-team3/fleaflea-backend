package com.anabada.fleaflea.domain.collection.repository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {
}
