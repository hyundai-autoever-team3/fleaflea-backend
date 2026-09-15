package com.anabada.fleaflea.domain.market.controller;

import com.anabada.fleaflea.domain.market.dto.MarketCreateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketCreateResponse;
import com.anabada.fleaflea.domain.market.service.MarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(
        name = "플리마켓",
        description = "플리마켓 생성 및 관리 API"
)
@RestController
@RequestMapping("/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;


    @PostMapping
    @Operation(
            summary = "플리마켓 생성",
            description = "로그인한 사용자가 플리마켓을 생성합니다. 개설자는 자동으로 참여자로 등록됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "플리마켓 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "회원 정보 없음")
    })
    public ResponseEntity<MarketCreateResponse> createMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MarketCreateRequest request
    ) {
        MarketCreateResponse response = marketService.createMarket(
                Long.valueOf(memberId),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}