package com.anabada.fleaflea.domain.market.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "markets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Market extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long marketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 100)
    private String inviteCode;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageKey;

    @Builder
    private Market(
            Member host,
            String title,
            String description,
            String coverImageKey,
            String inviteCode
    ) {
        this.host = host;
        this.title = title;
        this.description = description;
        this.coverImageKey = coverImageKey;
        this.inviteCode = inviteCode;
    }

    public static Market create(
            Member host,
            String title,
            String description,
            String coverImageKey,
            String inviteCode
    ) {
        return Market.builder()
                .host(host)
                .title(title)
                .description(description)
                .coverImageKey(coverImageKey)
                .inviteCode(inviteCode)
                .build();
    }

    public void update(
            String title,
            String description,
            String coverImageKey
    ) {
        if (title != null) {
            this.title = title;
        }

        if (description != null) {
            this.description = description;
        }

        if (coverImageKey != null) {
            this.coverImageKey = coverImageKey;
        }
    }

    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
