package com.anabada.fleaflea.domain.notification.controller;

import com.anabada.fleaflea.domain.notification.dto.PushDeviceRegisterRequest;
import com.anabada.fleaflea.domain.notification.dto.PushDeviceResponse;
import com.anabada.fleaflea.domain.notification.service.PushDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/devices")
@Tag(name = "알림")
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @PostMapping
    @Operation(summary = "푸시 기기 등록 및 갱신", description = "FCM 등록을 완료한 FID를 로그인 회원에게 연결합니다. 회원당 최대 10개이며 본인 FID 갱신은 개수 제한에서 제외합니다. 계정 전환 시 새 수신 회원의 한도를 적용합니다. Redis에서 모든 서버의 요청을 합산해 회원당 최근 60초 동안 최대 10회를 허용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 또는 갱신 완료, 기기 ID 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 FID 또는 플랫폼"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "409", description = "회원당 기기 등록 수 초과"),
            @ApiResponse(responseCode = "429", description = "최근 60초 기기 등록 요청 횟수 초과"),
            @ApiResponse(responseCode = "503", description = "등록 요청 제한 저장소를 일시적으로 사용할 수 없음")
    })
    public ResponseEntity<PushDeviceResponse> registerPushDevice(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushDeviceRegisterRequest pushDeviceRegisterRequest
    ) {
        Long deviceId = pushDeviceService.registerPushDevice(
                memberId,
                pushDeviceRegisterRequest.fid(),
                pushDeviceRegisterRequest.platform()
        );

        return ResponseEntity.ok(PushDeviceResponse.of(deviceId));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "푸시 기기 등록 해제", description = "본인 기기만 해제합니다. 없는 등록의 삭제도 성공합니다. 로그아웃은 모든 기기를 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 완료 또는 등록 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 기기 ID"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "다른 회원의 기기")
    })
    public ResponseEntity<Void> deletePushDevice(
            @AuthenticationPrincipal Long memberId,
            @PathVariable @Positive Long deviceId
    ) {
        pushDeviceService.deletePushDevice(memberId, deviceId);

        return ResponseEntity.noContent().build();
    }
}
