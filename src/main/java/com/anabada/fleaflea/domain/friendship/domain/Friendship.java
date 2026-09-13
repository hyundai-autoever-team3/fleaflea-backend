package com.anabada.fleaflea.domain.friendship.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
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

    @Column(nullable = false)
    private Long requesterId;

    @Column(nullable = false)
    private Long addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;
}
