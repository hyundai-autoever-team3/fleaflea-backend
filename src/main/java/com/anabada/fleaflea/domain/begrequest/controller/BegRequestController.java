package com.anabada.fleaflea.domain.begrequest.controller;

import com.anabada.fleaflea.domain.begrequest.dto.BeggingDetailResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingResponse;
import com.anabada.fleaflea.domain.begrequest.dto.BeggingRequest;
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
@Tag(name = "구걸 요청 ")
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
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청")
    })
    @GetMapping("/beg-requests/{begRequestId}")
    public ResponseEntity<BeggingDetailResponse> getBeggingDetails(
            @PathVariable Long begRequestId
    ) {
        return ResponseEntity.ok(
                begRequestService.getBeggingDetails(begRequestId)
        );
    }

    @Operation(summary = "구걸 요청 승인", description = "대기 중인 구걸 요청을 승인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "구걸 요청 승인 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "409", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/accept")
    public ResponseEntity<Void> acceptBeggingRequest(
            @PathVariable Long begRequestId) {
        begRequestService.acceptBeggingRequest(begRequestId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "구걸 요청 거절", description = "대기 중인 구걸 요청을 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "구걸 요청 거절 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "409", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/reject")
    public ResponseEntity<Void> rejectBeggingRequest(
            @PathVariable Long begRequestId) {
        begRequestService.rejectBeggingRequest(begRequestId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "구걸 요청 취소", description = "대기 중인 구걸 요청을 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "구걸 요청 취소 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 구걸 요청"),
            @ApiResponse(responseCode = "409", description = "대기 중인 구걸 요청이 아님")
    })
    @PostMapping("/beg-requests/{begRequestId}/cancel")
    public ResponseEntity<Void> cancelBeggingRequest(
            @PathVariable Long begRequestId) {
        begRequestService.cancelBeggingRequest(begRequestId);
        return ResponseEntity.noContent().build();
    }


}
