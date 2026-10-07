package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CollectionItemFixture {

    public static CollectionItem createCollectionItem(
            Member owner,
            String title,
            boolean isPublic
    ) {
        return CollectionItem.create(owner, title, "설명", null, isPublic);
    }

    public static CollectionItem createCollectionItemWithId(
            Long id,
            Member owner,
            String title,
            boolean isPublic
    ) {
        CollectionItem collectionItem = createCollectionItem(owner, title, isPublic);
        ReflectionTestUtils.setField(collectionItem, "collectionItemId", id);

        return collectionItem;
    }
}
