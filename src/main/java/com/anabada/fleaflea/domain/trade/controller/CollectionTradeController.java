package com.anabada.fleaflea.domain.trade.controller;

import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestResponse;
import com.anabada.fleaflea.domain.trade.service.CollectionTradeService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "도감 거래")
public class CollectionTradeController {
    private final CollectionTradeService service;

    @PostMapping("/collection-items/{collectionItemId}/trade-requests")
    @Operation(summary = "도감 거래 요청 생성")
    public ResponseEntity<CollectionTradeRequestResponse> createCollectionTradeRequest(
            @AuthenticationPrincipal Long memberId, @PathVariable Long collectionItemId,
            @Valid @RequestBody CollectionTradeRequestCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCollectionTradeRequest(memberId, collectionItemId, body));
    }

    @GetMapping("/collection-trade-requests/{id}")
    @Operation(summary = "도감 거래 요청 상세 조회")
    public CollectionTradeRequestResponse getCollectionTradeRequest(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return service.getCollectionTradeRequest(memberId, id);
    }

    @PostMapping("/collection-trade-requests/{id}/accept")
    @Operation(summary = "도감 거래 요청 수락")
    public CollectionTradeRequestResponse acceptCollectionTradeRequest(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return service.acceptCollectionTradeRequest(memberId, id);
    }

    @PostMapping("/collection-trade-requests/{id}/reject")
    @Operation(summary = "도감 거래 요청 거절")
    public CollectionTradeRequestResponse rejectCollectionTradeRequest(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return service.rejectCollectionTradeRequest(memberId, id);
    }

    @PostMapping("/collection-trade-requests/{id}/cancel")
    @Operation(summary = "도감 거래 요청 취소")
    public CollectionTradeRequestResponse cancelCollectionTradeRequest(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return service.cancelCollectionTradeRequest(memberId, id);
    }

    @PostMapping("/collection-trade-requests/{id}/complete")
    @Operation(
            summary = "도감 거래 완료",
            description = "요청자가 수락된 도감 거래의 완료를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 완료 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "도감 거래 요청자가 아님"),
            @ApiResponse(responseCode = "404", description = "도감 거래 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "수락된 거래가 아니거나 이미 완료된 거래")
    })
    public CollectionTradeRequestResponse completeCollectionTradeRequest(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return service.completeCollectionTradeRequest(memberId, id);
    }
}
