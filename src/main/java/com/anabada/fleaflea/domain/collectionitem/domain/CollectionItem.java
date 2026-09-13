package com.anabada.fleaflea.domain.collectionitem.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

    @Column(nullable = false)
    private Long memberId;

    @Column(length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "is_public")
    private Boolean isPublic;
}