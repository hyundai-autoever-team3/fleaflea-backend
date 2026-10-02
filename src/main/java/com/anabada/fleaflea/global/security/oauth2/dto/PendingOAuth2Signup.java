package com.anabada.fleaflea.global.security.oauth2.dto;

import com.anabada.fleaflea.domain.member.dto.OAuth2MemberInfo;

import java.time.LocalDateTime;

public record PendingOAuth2Signup(
        OAuth2MemberInfo memberInfo,
        LocalDateTime expiresAt
) {
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
