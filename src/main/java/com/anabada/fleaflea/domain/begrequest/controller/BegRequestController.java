package com.anabada.fleaflea.domain.begrequest.controller;

import com.anabada.fleaflea.domain.begrequest.dto.BeggingDetailResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingRequest;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingStatusResponse;
import com.anabada.fleaflea.domain.begrequest.service.BegRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "구걸 요청")
public class BegRequestController {
    private final BegRequestService begRequestService;

    @Operation(summary = "구걸 요청 생성", description = "현재 로그인한 사용자가 다른 사용자의 도감 아이템을 구걸하는 요청을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "구걸 요청 생성 성공",
                    content = @Content(schema = @Schema(implementation = BeggingResponse.class))),
            @ApiResponse(responseCode = "404", description = "도감 아이템을 찾을 수 없음"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @PostMapping("/collection-items/{collectionItemId}/beg-requests")
    public ResponseEntity<BeggingResponse> createBegging(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long collectionItemId,
            @Valid @RequestBody BeggingRequest request
    ) {
        BeggingResponse response =
                begRequestService.createBegging(memberId, collectionItemId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "구걸 요청 상세 조회", description = "구걸 요청 ID를 통해 구걸 요청의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구걸 요청 상세 조회 성공"),
            @ApiResponse(responseCode = "403", description = "구걸 요청에 접근할 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청")
    })
    @GetMapping("/beg-requests/{begRequestId}")
    public ResponseEntity<BeggingDetailResponse> getBeggingDetails(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long begRequestId
    ) {
        return ResponseEntity.ok(
                begRequestService.getBeggingDetails(memberId, begRequestId)
        );
    }

    @Operation(
            summary = "구걸 요청 수락",
            description = "대기 중인 구걸 요청을 수락합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구걸 요청 수락 성공"),
            @ApiResponse(responseCode = "403", description = "구걸 요청에 접근할 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "400", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/accept")
    public ResponseEntity<BeggingStatusResponse> acceptBeggingRequest(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long begRequestId
    ) {
        BeggingStatusResponse response =
                begRequestService.acceptBeggingRequest(memberId, begRequestId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "구걸 요청 거절",
            description = "대기 중인 구걸 요청을 거절합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구걸 요청 거절 성공"),
            @ApiResponse(responseCode = "403", description = "구걸 요청에 접근할 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "400", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/reject")
    public ResponseEntity<BeggingStatusResponse> rejectBeggingRequest(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long begRequestId
    ) {
        BeggingStatusResponse response =
                begRequestService.rejectBeggingRequest(memberId, begRequestId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "구걸 요청 취소",
            description = "대기 중인 구걸 요청을 취소합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구걸 요청 취소 성공"),
            @ApiResponse(responseCode = "403", description = "구걸 요청에 접근할 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "400", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/cancel")
    public ResponseEntity<BeggingStatusResponse> cancelBeggingRequest(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long begRequestId
    ) {
        BeggingStatusResponse response =
                begRequestService.cancelBeggingRequest(memberId, begRequestId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "구걸 요청 완료",
            description = "수락된 구걸 요청을 완료 처리하고 거래 내역을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구걸 요청 완료 성공"),
            @ApiResponse(responseCode = "403", description = "구걸 요청에 접근할 권한이 없음"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "400", description = "수락된 구걸 요청이 아님"),
            @ApiResponse(responseCode = "409", description = "이미 완료된 구걸 요청")
    })
    @PostMapping("/beg-requests/{begRequestId}/complete")
    public ResponseEntity<BeggingStatusResponse> completeBeggingRequest(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long begRequestId
    ) {
        BeggingStatusResponse response =
                begRequestService.completeBeggingRequest(memberId, begRequestId);

        return ResponseEntity.ok(response);
    }


}
