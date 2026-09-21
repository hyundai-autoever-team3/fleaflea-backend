package com.anabada.fleaflea.domain.notification.controller;

import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import com.anabada.fleaflea.domain.notification.dto.NotificationUnreadCountResponse;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "알림")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @GetMapping(value = "/notifications/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "알림 실시간 구독(SSE)", description = """
            서버가 알림을 푸시하는 단방향 스트림.

            - 이벤트: connect(연결 확인), notification(신규 알림), :heartbeat(주석, 무시)
            - EventSource는 Authorization 헤더를 못 보내므로 fetch 기반 클라이언트를 사용할 것
            - 재연결 후에는 unread-count를 한 번 조회해 끊겨 있던 동안의 알림을 보정할 것
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 성공(text/event-stream)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal Long memberId
    ) {
        SseEmitter emitter = notificationSseService.subscribe(memberId);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping("/notifications")
    @Operation(summary = "알림 목록 조회", description = "최신 생성순. page는 0부터, size는 1~100")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "알림 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "페이지 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        PageResponse<NotificationResponse> response =
                notificationService.getNotifications(memberId, page, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "미확인 알림 개수 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미확인 알림 개수 조회 성공",
                    content = @Content(schema = @Schema(implementation = NotificationUnreadCountResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long memberId
    ) {
        NotificationUnreadCountResponse response =
                notificationService.getUnreadCount(memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "알림 읽음 처리 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "해당 알림의 수신자가 아님"),
            @ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음")
    })
    public ResponseEntity<Void> readNotification(
            @PathVariable @Positive Long notificationId,
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.readNotification(notificationId, memberId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/notifications/read-all")
    @Operation(summary = "전체 알림 읽음 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "전체 알림 읽음 처리 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<Void> readAllNotifications(
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.readAllNotifications(memberId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/notifications/{notificationId}")
    @Operation(summary = "알림 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "알림 삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "해당 알림의 수신자가 아님"),
            @ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteNotification(
            @PathVariable @Positive Long notificationId,
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.deleteNotification(notificationId, memberId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/notifications")
    @Operation(summary = "전체 알림 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "전체 알림 삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<Void> deleteAllNotifications(
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.deleteAllNotifications(memberId);

        return ResponseEntity.noContent().build();
    }
}