package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record ProfileUpdateRequest(

        @Schema(description = "변경할 닉네임", example = "jongpil")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname,

        @Schema(description = "변경할 프로필 이미지")
        MultipartFile profileImage,

        @Schema(description = "프로필 이미지 삭제 여부", example = "false")
        boolean deleteProfileImage
) {

}
