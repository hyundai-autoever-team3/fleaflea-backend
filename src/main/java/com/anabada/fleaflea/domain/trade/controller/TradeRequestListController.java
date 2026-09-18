package com.anabada.fleaflea.domain.trade.controller;

import com.anabada.fleaflea.domain.trade.dto.TradeRequestListResponse;
import com.anabada.fleaflea.domain.trade.service.TradeRequestListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trade-requests")
@Tag(name = "거래 요청")
public class TradeRequestListController {

    private final TradeRequestListService tradeRequestListService;

    @GetMapping
    @Operation(
            summary = "내 거래 요청 통합 목록 조회",
            description = "플리마켓 상품 거래, 도감 거래, 구걸 요청을 최신순으로 조회합니다. "
                    + "direction=received는 받은 요청, direction=sent는 보낸 요청입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거래 요청 목록 조회 성공",
                    content = @Content(array = @ArraySchema(schema =
                            @Schema(implementation = TradeRequestListResponse.class)))),
            @ApiResponse(responseCode = "400", description = "direction은 received 또는 sent여야 함"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<List<TradeRequestListResponse>> list(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "요청 방향: received 또는 sent", required = true,
                    example = "received")
            @RequestParam String direction
    ) {
        return ResponseEntity.ok(tradeRequestListService.list(memberId, direction));
    }
}
