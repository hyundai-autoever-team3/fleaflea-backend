package com.anabada.fleaflea.domain.member.dto;

public record LoginRequest(
    String email,
    String password
) {
}
