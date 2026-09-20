package com.anabada.fleaflea.domain.trade.controller;

import com.anabada.fleaflea.domain.trade.dto.request.TradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestCreateResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestDetailResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestStatusResponse;
import com.anabada.fleaflea.domain.trade.dto.response.TradeRequestSummaryResponse;
import com.anabada.fleaflea.domain.trade.service.TradeRequestService;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "거래 요청")
public class TradeRequestController {

    private final TradeRequestService tradeRequestService;

    @PostMapping("/items/{itemId}/trade-requests")
    @Operation(summary = "거래 요청 생성", description = "플리마켓 참여자가 거래 가능한 다른 회원의 상품에 요청")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 생성 성공",
                    content = @Content(schema = @Schema(implementation = TradeRequestCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값, 대여 기간이 올바르지 않거나 자신의 상품에 요청"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품이 등록된 플리마켓의 참여자가 아님"),
            @ApiResponse(responseCode = "404", description = "상품 또는 회원을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 요청했거나 거래 요청이 불가능한 상품")
    })
    public ResponseEntity<TradeRequestCreateResponse> createTradeRequest(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TradeRequestCreateRequest request
    ) {
        TradeRequestCreateResponse response = tradeRequestService.createTradeRequest(
                itemId,
                memberId,
                request
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/item-trade-requests/{requestId}")
    @Operation(summary = "거래 요청 상세 조회", description = "거래 요청자와 상품 판매자만 조회 가능")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = TradeRequestDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "거래 당사자가 아님"),
            @ApiResponse(responseCode = "404", description = "거래 요청을 찾을 수 없음")
    })
    public ResponseEntity<TradeRequestDetailResponse> getTradeRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                tradeRequestService.getTradeRequest(requestId, memberId)
        );
    }

    @GetMapping("/item-trade-requests")
    @Operation(
            summary = "내 거래 요청 목록 조회",
            description = "요청자 또는 판매자로 참여한 거래를 최신순으로 조회. page는 0부터, size는 1~100"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "페이지 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<PageResponse<TradeRequestSummaryResponse>> getTradeRequests(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(tradeRequestService.getTradeRequests(memberId, page, size));
    }

    @PostMapping("/item-trade-requests/{requestId}/accept")
    @Operation(summary = "거래 요청 수락", description = "상품 판매자만 대기 중인 거래 요청을 수락 가능")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 수락 성공. 상태: ACCEPTED",
                    content = @Content(schema = @Schema(implementation = TradeRequestStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품 판매자가 아님"),
            @ApiResponse(responseCode = "404", description = "거래 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "대기 중인 요청이 아니거나 상품이 거래 가능한 상태가 아님")
    })
    public ResponseEntity<TradeRequestStatusResponse> acceptTradeRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                tradeRequestService.acceptTradeRequest(requestId, memberId)
        );
    }

    @PostMapping("/item-trade-requests/{requestId}/reject")
    @Operation(summary = "거래 요청 거절", description = "상품 판매자만 대기 중인 거래 요청을 거절 가능")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 거절 성공. 상태: REJECTED",
                    content = @Content(schema = @Schema(implementation = TradeRequestStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품 판매자가 아님"),
            @ApiResponse(responseCode = "404", description = "거래 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "대기 중인 거래 요청이 아님")
    })
    public ResponseEntity<TradeRequestStatusResponse> rejectTradeRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                tradeRequestService.rejectTradeRequest(requestId, memberId)
        );
    }

    @PostMapping("/item-trade-requests/{requestId}/cancel")
    @Operation(summary = "거래 요청 취소", description = "거래 요청자만 대기 중인 자신의 요청을 취소 가능")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 취소 성공. 상태: CANCELLED",
                    content = @Content(schema = @Schema(implementation = TradeRequestStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "거래 요청자가 아님"),
            @ApiResponse(responseCode = "404", description = "거래 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "대기 중인 거래 요청이 아님")
    })
    public ResponseEntity<TradeRequestStatusResponse> cancelTradeRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                tradeRequestService.cancelTradeRequest(requestId, memberId)
        );
    }

    @PostMapping("/item-trade-requests/{requestId}/complete")
    @Operation(
            summary = "거래 완료 확인",
            description = "거래 당사자가 수락된 거래의 완료를 확인."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 확인 성공. 상태: COMPLETED",
                    content = @Content(schema = @Schema(implementation = TradeRequestStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "거래 당사자가 아님"),
            @ApiResponse(responseCode = "404", description = "거래 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "수락된 거래가 아니거나 이미 완료 확인한 거래")
    })
    public ResponseEntity<TradeRequestStatusResponse> confirmTradeRequestCompletion(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                tradeRequestService.confirmTradeRequestCompletion(requestId, memberId)
        );
    }
}
