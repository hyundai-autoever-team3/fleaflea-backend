package com.anabada.fleaflea.domain.member.dto;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
