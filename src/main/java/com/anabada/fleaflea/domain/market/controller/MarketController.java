package com.anabada.fleaflea.domain.market.controller;

import com.anabada.fleaflea.domain.market.dto.MarketCreateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketCreateResponse;
import com.anabada.fleaflea.domain.market.dto.MarketDetailResponse;
import com.anabada.fleaflea.domain.market.dto.MarketSummaryResponse;
import com.anabada.fleaflea.domain.market.dto.MarketSearchCondition;
import com.anabada.fleaflea.domain.market.service.MarketQueryService;
import com.anabada.fleaflea.domain.market.service.MarketService;
import com.anabada.fleaflea.domain.marketmember.dto.MarketMemberResponse;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.anabada.fleaflea.domain.market.dto.MarketInvitationResponse;
import com.anabada.fleaflea.domain.market.dto.MarketUpdateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketUpdateResponse;

@Tag(
        name = "플리마켓",
        description = "플리마켓 생성 및 관리 API"
)
@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final MarketQueryService marketQueryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute MarketCreateRequest request
    ) {
        MarketCreateResponse response =
                marketService.createMarket(memberId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    @Operation(
            summary = "내 플리마켓 목록 조회",
            description = "로그인한 사용자가 참여 중인 플리마켓 목록을 최근 참여 순으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "플리마켓 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 조회 범위"),
            @ApiResponse(responseCode = "403", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "회원 정보 없음")
    })
    public ResponseEntity<PageResponse<MarketSummaryResponse>> getMarkets(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "조회 범위",
                    example = "joined",
                    required = true
            )
            @RequestParam(defaultValue = "joined") String scope,

            @ParameterObject MarketSearchCondition condition,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "joinedAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        PageResponse<MarketSummaryResponse> response =
                marketQueryService.getMarkets(
                        memberId,
                        scope,
                        condition,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{marketId}")
    @Operation(
            summary = "플리마켓 상세 조회",
            description = "로그인한 사용자가 참여 중인 플리마켓의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "플리마켓 상세 조회 성공"),
            @ApiResponse(responseCode = "403", description = "미참여 사용자의 조회 요청"),
            @ApiResponse(responseCode = "404", description = "회원 또는 플리마켓 정보 없음")
    })
    public ResponseEntity<MarketDetailResponse> getMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(description = "플리마켓 ID", example = "1")
            @PathVariable Long marketId
    ) {
        MarketDetailResponse response =
                marketQueryService.getMarket(memberId, marketId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{marketId}/members")
    @Operation(
            summary = "플리마켓 참여자 목록 조회",
            description = "플리마켓 참여자가 해당 플리마켓의 참여자 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "참여자 목록 조회 성공"),
            @ApiResponse(responseCode = "403", description = "미참여 사용자의 조회 요청"),
            @ApiResponse(responseCode = "404", description = "회원 또는 플리마켓 정보 없음")
    })
    public ResponseEntity<PageResponse<MarketMemberResponse>> getMarketMembers(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(description = "플리마켓 ID", example = "1")
            @PathVariable Long marketId,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "joinedAt",
                    direction = Sort.Direction.ASC
            ) Pageable pageable
    ) {
        PageResponse<MarketMemberResponse> response =
                marketQueryService.getMarketMembers(
                        memberId,
                        marketId,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PatchMapping(
            value = "/{marketId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "플리마켓 수정")
    public ResponseEntity<MarketUpdateResponse> updateMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long marketId,

            @Valid @ModelAttribute MarketUpdateRequest request
    ) {
        MarketUpdateResponse response =
                marketService.updateMarket(
                        memberId,
                        marketId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{marketId}/invitation")
    @Operation(summary = "초대 코드 재발급")
    public ResponseEntity<MarketInvitationResponse> reissueInvitation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long marketId
    ) {
        return ResponseEntity.ok(
                marketService.reissueInvitation(memberId, marketId)
        );
    }

    @DeleteMapping("/{marketId}/members/me")
    @Operation(summary = "플리마켓 나가기")
    public ResponseEntity<Void> leaveMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long marketId
    ) {
        marketService.leaveMarket(memberId, marketId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{marketId}")
    @Operation(summary = "플리마켓 삭제")
    public ResponseEntity<Void> deleteMarket(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long marketId
    ) {
        marketService.deleteMarket(memberId, marketId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{marketId}/invitation")
    @Operation(
            summary = "초대 코드 조회",
            description = "플리마켓 개설자가 현재 초대 코드를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초대 코드 조회 성공"),
            @ApiResponse(responseCode = "403", description = "개설자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "플리마켓 정보 없음")
    })
    public ResponseEntity<MarketInvitationResponse> getInvitation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(description = "플리마켓 ID", example = "1")
            @PathVariable Long marketId
    ) {
        MarketInvitationResponse response =
                marketService.getInvitation(memberId, marketId);

        return ResponseEntity.ok(response);
    }
}
