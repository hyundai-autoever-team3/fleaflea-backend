package com.anabada.fleaflea.domain.begrequest.domain;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "beg_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BegRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long begRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_item_id")
    private CollectionItem collectionItem;

    @Column(name = "collection_item_snapshot_id", nullable = false)
    private Long collectionItemSnapshotId;

    @Column(name = "collection_item_title", nullable = false, length = 150)
    private String collectionItemTitle;

    @Column(name = "collection_item_description", columnDefinition = "TEXT")
    private String collectionItemDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Member applicant;

    @Column(columnDefinition = "TEXT")
    private String story;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BegRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Builder
    private BegRequest(
            CollectionItem collectionItem,
            Member applicant,
            Member owner,
            String story,
            BegRequestStatus status
    ) {
        this.collectionItem = collectionItem;
        this.collectionItemSnapshotId = collectionItem.getCollectionItemId();
        this.collectionItemTitle = collectionItem.getTitle();
        this.collectionItemDescription = collectionItem.getDescription();
        this.applicant = applicant;
        this.owner = owner;
        this.story = story;
        this.status = status;
    }

    public static BegRequest create(
            CollectionItem collectionItem,
            Member applicant,
            String story,
            BegRequestStatus status
    ) {
        return BegRequest.builder()
                .collectionItem(collectionItem)
                .applicant(applicant)
                .owner(collectionItem.getOwner())
                .story(story)
                .status(status)
                .build();
    }

    public void accept() {
        this.status = BegRequestStatus.ACCEPTED;
    }

    public void reject() {
        this.status = BegRequestStatus.REJECTED;
    }

    public void cancel() { this.status = BegRequestStatus.CANCELLED; }

    public void complete() { this.status = BegRequestStatus.COMPLETED; }

}
