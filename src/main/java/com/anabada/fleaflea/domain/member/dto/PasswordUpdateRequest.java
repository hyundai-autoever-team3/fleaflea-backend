package com.anabada.fleaflea.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(

        @NotBlank(message = "비밀번호는 필수 값입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수 값입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        String newPassword
) {
}
