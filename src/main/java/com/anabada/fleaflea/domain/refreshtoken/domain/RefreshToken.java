package com.anabada.fleaflea.domain.refreshtoken.domain;

import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Ref;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long refreshTokenId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 500)
    private String refreshToken;

    private LocalDateTime expiresAt;

    @Builder
    private RefreshToken(
            Long memberId,
            String refreshToken,
            LocalDateTime expiresAt
    ) {
        this.memberId = memberId;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(
            Long memberId,
            String refreshToken,
            LocalDateTime expiresAt
    ){
        return RefreshToken.builder()
                .memberId(memberId)
                .refreshToken(refreshToken)
                .expiresAt(expiresAt)
                .build();
    }

    public void update(String refreshToken, LocalDateTime expiresAt) {
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

}
