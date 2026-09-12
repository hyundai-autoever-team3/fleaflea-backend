package com.anabada.fleaflea.domain.marketmember.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "market_members")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long marketMemberId;

    @Column(nullable = false)
    private Long marketId;

    @Column(nullable = false)
    private Long memberId;

    @CreatedDate
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}
