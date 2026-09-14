package com.anabada.fleaflea.domain.marketmember.controller;

import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinRequest;
import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinResponse;
import com.anabada.fleaflea.domain.marketmember.service.MarketJoinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(
        name = "플리마켓",
        description = "플리마켓 생성 및 관리 API"
)
@RestController
@RequestMapping("/market-joins")
@RequiredArgsConstructor
public class MarketJoinController {

    private final MarketJoinService marketJoinService;

    @PostMapping
    @Operation(
            summary = "초대 코드로 플리마켓 참여",
            description = "로그인한 사용자가 초대 코드를 입력하여 플리마켓에 참여합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "플리마켓 참여 성공"),
            @ApiResponse(responseCode = "400", description = "초대 코드 미입력"),
            @ApiResponse(responseCode = "403", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "회원 또는 초대 코드 없음"),
            @ApiResponse(responseCode = "409", description = "이미 참여한 플리마켓")
    })
    public ResponseEntity<MarketJoinResponse> joinMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MarketJoinRequest request
    ) {
        MarketJoinResponse response = marketJoinService.joinMarket(
                Long.valueOf(memberId),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}