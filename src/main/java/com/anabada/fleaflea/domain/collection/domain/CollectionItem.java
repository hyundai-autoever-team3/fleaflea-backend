package com.anabada.fleaflea.domain.collection.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "collection_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collectionItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageKey;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;


    @Builder
    private CollectionItem(
            Member owner,
            String title,
            String description,
            String imageKey,
            Boolean isPublic
    ) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.imageKey = imageKey;
        this.isPublic = isPublic;
    }

    public static CollectionItem create(
            Member owner,
            String title,
            String description,
            String imageKey,
            Boolean isPublic
    ) {
        return CollectionItem.builder()
                .owner(owner)
                .title(title)
                .description(description)
                .imageKey(imageKey)
                .isPublic(isPublic)
                .build();
    }

    public void update(
            String title,
            String description,
            Boolean isPublic
    ) {
        if (title != null) {
            this.title = title;
        }

        if (description != null) {
            this.description = description;
        }

        if (isPublic != null) {
            this.isPublic = isPublic;
        }
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getMemberId().equals(memberId);
    }
}
