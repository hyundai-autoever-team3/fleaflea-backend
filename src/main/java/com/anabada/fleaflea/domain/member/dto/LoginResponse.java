package com.anabada.fleaflea.domain.member.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
