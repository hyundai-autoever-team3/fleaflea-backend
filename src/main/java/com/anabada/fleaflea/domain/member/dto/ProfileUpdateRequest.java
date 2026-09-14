package com.anabada.fleaflea.domain.member.dto;

public record ProfileUpdateRequest(
        String nickname,
        String profileImageUrl
) {

}
