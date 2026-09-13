package com.anabada.fleaflea.domain.refreshtoken.domain;

import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

}
