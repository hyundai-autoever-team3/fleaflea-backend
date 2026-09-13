package com.anabada.fleaflea.domain.member.dto;



public record SignUpRequest (
    String email,
    String password,
    String nickname,
    String profileImageUrl
) {
}
