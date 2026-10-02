package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.OAuth2Provider;

public record OAuth2MemberInfo(
        OAuth2Provider provider,
        String providerId,
        String email,
        String nickname
) {
}
