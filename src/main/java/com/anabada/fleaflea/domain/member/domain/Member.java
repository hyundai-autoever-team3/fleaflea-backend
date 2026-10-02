package com.anabada.fleaflea.domain.member.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth2_provider")
    private OAuth2Provider oauth2Provider;

    private String oauth2Id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(columnDefinition = "TEXT")
    private String profileImageKey;

    @Builder
    private Member(
            String email,
            String password,
            String nickname,
            OAuth2Provider oauth2Provider,
            String oauth2Id
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.oauth2Provider = oauth2Provider;
        this.oauth2Id = oauth2Id;
    }

    public static Member create(
            String email,
            String password,
            String nickname
    ) {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .build();
    }

    public static Member createOAuth2(
            OAuth2Provider provider,
            String oauth2Id,
            String email,
            String password,
            String nickname
    ) {
        return Member.builder()
                .oauth2Provider(provider)
                .oauth2Id(oauth2Id)
                .email(email)
                .password(password)
                .nickname(nickname)
                .build();
    }

    public void updateProfile(
            String nickname,
            String profileImageKey
    ) {
        this.nickname = nickname;
        this.profileImageKey = profileImageKey;
    }

    public void updatePassword(
            String password
    ) {
        this.password = password;
    }


}
