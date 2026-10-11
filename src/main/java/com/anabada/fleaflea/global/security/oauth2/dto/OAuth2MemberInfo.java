package com.anabada.fleaflea.global.security.oauth2.dto;

import com.anabada.fleaflea.domain.member.domain.SocialProvider;

public record OAuth2MemberInfo(
        SocialProvider provider,
        String providerId,
        String email,
        String nickname
) {
}
