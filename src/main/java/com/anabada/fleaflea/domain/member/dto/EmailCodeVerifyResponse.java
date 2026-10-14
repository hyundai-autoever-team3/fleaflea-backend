package com.anabada.fleaflea.domain.member.dto;

public record EmailCodeVerifyResponse(
        String result,
        String verificationToken
) {
}