package com.anabada.fleaflea.domain.marketmember.domain;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "market_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_member",
                        columnNames = {"market_id", "member_id"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long marketMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @CreatedDate
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Builder
    private MarketMember(Market market, Member member) {
        this.market = market;
        this.member = member;
    }

    public static MarketMember create(Market market, Member member) {
        return MarketMember.builder()
                .market(market)
                .member(member)
                .build();
    }
}