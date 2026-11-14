package com.anabada.fleaflea.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "푸시 기기 등록 결과")
public record PushDeviceResponse(
        @Schema(description = "기기 등록 해제에 사용하는 서버 식별자", example = "1")
        Long deviceId
) {
    public static PushDeviceResponse of(Long deviceId) {
        return new PushDeviceResponse(deviceId);
    }
}
