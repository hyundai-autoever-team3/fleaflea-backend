package com.anabada.fleaflea.domain.member.dto;

public record PasswordUpdateRequest(
        String currentPassword,
        String newPassword
) {
}
