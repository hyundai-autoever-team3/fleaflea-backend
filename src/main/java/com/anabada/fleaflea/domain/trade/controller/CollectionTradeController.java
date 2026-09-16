package com.anabada.fleaflea.domain.trade.controller;

import com.anabada.fleaflea.domain.trade.dto.CollectionTradeCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeResponse;
import com.anabada.fleaflea.domain.trade.service.CollectionTradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "도감 거래",
        description = "도감 아이템 거래 요청 및 완료 API"
)
@RestController
@RequiredArgsConstructor
public class CollectionTradeController {

    private final CollectionTradeService collectionTradeService;

    @PostMapping(
            "/collection-items/{collectionItemId}/trade-requests"
    )
    @Operation(summary = "도감 아이템 거래 요청 생성")
    public ResponseEntity<CollectionTradeResponse> createTradeRequest(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long collectionItemId,

            @Valid @RequestBody
            CollectionTradeCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(collectionTradeService.createTradeRequest(
                        memberId,
                        collectionItemId,
                        request
                ));
    }

    @GetMapping("/collection-trade-requests/{requestId}")
    @Operation(summary = "도감 거래 요청 상세 조회")
    public ResponseEntity<CollectionTradeResponse> getTradeRequest(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                collectionTradeService.getTradeRequest(
                        memberId,
                        requestId
                )
        );
    }

    @PostMapping(
            "/collection-trade-requests/{requestId}/accept"
    )
    @Operation(summary = "도감 거래 요청 수락")
    public ResponseEntity<CollectionTradeResponse> acceptTradeRequest(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                collectionTradeService.acceptTradeRequest(
                        memberId,
                        requestId
                )
        );
    }

    @PostMapping(
            "/collection-trade-requests/{requestId}/reject"
    )
    @Operation(summary = "도감 거래 요청 거절")
    public ResponseEntity<CollectionTradeResponse> rejectTradeRequest(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                collectionTradeService.rejectTradeRequest(
                        memberId,
                        requestId
                )
        );
    }

    @PostMapping(
            "/collection-trade-requests/{requestId}/cancel"
    )
    @Operation(summary = "도감 거래 요청 취소")
    public ResponseEntity<CollectionTradeResponse> cancelTradeRequest(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                collectionTradeService.cancelTradeRequest(
                        memberId,
                        requestId
                )
        );
    }

    @PostMapping(
            "/collection-trade-requests/{requestId}/completion-confirmations"
    )
    @Operation(summary = "도감 거래 완료 확인")
    public ResponseEntity<CollectionTradeResponse> confirmCompletion(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                collectionTradeService.confirmCompletion(
                        memberId,
                        requestId
                )
        );
    }
}