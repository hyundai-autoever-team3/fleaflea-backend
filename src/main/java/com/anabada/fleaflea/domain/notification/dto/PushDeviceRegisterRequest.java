package com.anabada.fleaflea.domain.notification.dto;

import com.anabada.fleaflea.domain.notification.domain.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "FCM 기기 등록 요청")
public record PushDeviceRegisterRequest(
        @NotBlank(message = "FID는 필수입니다.")
        @Pattern(regexp = "[A-Za-z0-9_-]{22}", message = "유효한 FID를 입력해주세요.")
        @Schema(description = "FCM 등록을 완료한 Firebase 설치 식별자. 영문, 숫자, 밑줄 또는 하이픈으로 구성된 22자 FID",
                example = "c123456789012345678901", minLength = 22, maxLength = 22,
                pattern = "[A-Za-z0-9_-]{22}")
        String fid,

        @NotNull(message = "플랫폼은 필수입니다.")
        @Schema(description = "기기 플랫폼. 현재 웹/PWA는 WEB을 사용합니다.", example = "WEB")
        PushPlatform platform
) {

    public static PushDeviceRegisterRequest of(String fid, PushPlatform platform) {
        return new PushDeviceRegisterRequest(fid, platform);
    }
}
