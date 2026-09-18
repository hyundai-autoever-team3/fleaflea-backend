package com.anabada.fleaflea.domain.poke.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_pokes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPoke extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pokeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    private MemberPoke(Member sender, Member recipient) {
        this.sender = sender;
        this.recipient = recipient;
    }

    public static MemberPoke create(Member sender, Member recipient) {
        return new MemberPoke(sender, recipient);
    }

    public void markRead() {
        isRead = true;
    }
}
