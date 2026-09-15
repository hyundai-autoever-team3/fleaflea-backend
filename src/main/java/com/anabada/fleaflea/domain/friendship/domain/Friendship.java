package com.anabada.fleaflea.domain.friendship.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "friendships")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long friendshipId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private Member addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Builder
    private Friendship(
            Member requester,
            Member addressee,
            FriendshipStatus status
    ) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = status;
    }

    public static Friendship create(
            Member requester,
            Member addressee,
            FriendshipStatus status
    ) {
        return Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(status)
                .build();
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public void reject() {
        this.status = FriendshipStatus.REJECTED;
    }

    public void cancel() {
        this.status = FriendshipStatus.PENDING;
    }
}
