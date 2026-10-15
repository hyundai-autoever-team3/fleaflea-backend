package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(

        @NotBlank(message = "비밀번호는 필수 값입니다.")
        @Schema(description = "현재 비밀번호", example = "CurrentPassword123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수 값입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        @Schema(description = "새 비밀번호(8~20자)", example = "NewPassword123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String newPassword
) {
}
