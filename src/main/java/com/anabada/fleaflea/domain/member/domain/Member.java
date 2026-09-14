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

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    @Builder
    private Member(
            String email,
            String password,
            String nickname,
            String profileImageUrl
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static Member create(
            String email,
            String password,
            String nickname,
            String profileImageUrl
    ) {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    public void updateProfile(
            String nickname,
            String profileImageUrl
    ) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }


}
