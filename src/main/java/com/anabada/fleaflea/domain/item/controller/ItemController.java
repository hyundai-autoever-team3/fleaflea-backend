package com.anabada.fleaflea.domain.item.controller;

import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.item.dto.ItemCreateRequest;
import com.anabada.fleaflea.domain.item.dto.ItemDetailResponse;
import com.anabada.fleaflea.domain.item.dto.ItemSummaryResponse;
import com.anabada.fleaflea.domain.item.dto.ItemUpdateRequest;
import com.anabada.fleaflea.domain.item.service.ItemService;
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
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "플리마켓 상품")
public class ItemController {

    private final ItemService itemService;

    @PostMapping(
            value = "/markets/{marketId}/items",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "상품 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "상품 등록 성공",
                    content = @Content(schema = @Schema(implementation = ItemSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 상품 정보가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "플리마켓 미참여 또는 도감 아이템 소유자가 아님"),
            @ApiResponse(responseCode = "404", description = "플리마켓, 회원 또는 도감 아이템을 찾을 수 없음")
    })
    public ResponseEntity<ItemSummaryResponse> createItem(
            @PathVariable @Positive Long marketId,
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute ItemCreateRequest request
    ) {
        ItemSummaryResponse response = itemService.createItem(marketId, memberId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/markets/{marketId}/items")
    @Operation(summary = "상품 목록 조회", description = "최신 등록순. page는 0부터, size는 1~100")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "페이지 또는 필터 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "플리마켓 참여자가 아님"),
            @ApiResponse(responseCode = "404", description = "플리마켓을 찾을 수 없음")
    })
    public ResponseEntity<PageResponse<ItemSummaryResponse>> findItems(
            @PathVariable Long marketId,
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ItemTradeType tradeType,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String keyword
    ) {
        PageResponse<ItemSummaryResponse> response = itemService.findItems(
                marketId, memberId, page, size, tradeType, status, keyword
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/items/{itemId}")
    @Operation(summary = "상품 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상품 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = ItemDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품이 등록된 플리마켓의 참여자가 아님"),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    public ResponseEntity<ItemDetailResponse> findItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long memberId
    ) {
        ItemDetailResponse response = itemService.findItem(itemId, memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping(
            value = "/items/{itemId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "상품 정보 수정", description = "등록자만 거래 완료 전에 수정 가능")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상품 정보 수정 성공", content = @Content(schema = @Schema(implementation = ItemDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품 등록자가 아님"),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "거래 완료된 상품은 수정할 수 없음")
    })
    public ResponseEntity<ItemDetailResponse> updateItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute ItemUpdateRequest request
    ) {
        ItemDetailResponse response = itemService.updateItem(itemId, memberId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "상품 삭제", description = "등록자만 삭제 가능. 거래 진행 중에는 삭제 불가")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "상품 삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "상품 등록자가 아님"),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "거래 진행 중인 상품은 삭제할 수 없음")
    })
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long memberId
    ) {
        itemService.deleteItem(itemId, memberId);

        return ResponseEntity.noContent().build();
    }
}