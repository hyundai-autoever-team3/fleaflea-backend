package com.anabada.fleaflea.domain.collectionitem.controller;

import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemCreateRequest;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSummaryResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.service.CollectionItemService;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import com.anabada.fleaflea.domain.collectionitem.dto.MemberCollectionResponse;

@Tag(
        name = "도감 아이템",
        description = "도감 아이템 등록 및 관리 API"
)
@RestController
@RequiredArgsConstructor
public class CollectionItemController {

    private final CollectionItemService collectionItemService;

    @PostMapping(
            value = "/collection-items",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "도감 아이템 등록")
    public ResponseEntity<CollectionItemResponse> createCollectionItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Valid @ModelAttribute
            CollectionItemCreateRequest request
    ) {
        CollectionItemResponse response =
                collectionItemService.createCollectionItem(
                        memberId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/members/me/collection-items")
    @Operation(summary = "내 도감 목록 조회")
    public ResponseEntity<PageResponse<CollectionItemSummaryResponse>>
    getMyCollectionItems(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                collectionItemService.getMyCollectionItems(
                        memberId,
                        pageable
                )
        );
    }

    @GetMapping("/members/{ownerId}/collection-items")
    @Operation(
            summary = "다른 회원의 공개 도감 목록 조회",
            description = "조회 권한 확인 후 소유자 정보와 공개 아이템 목록을 함께 반환합니다."
    )
    public ResponseEntity<MemberCollectionResponse> getMemberCollectionItems(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long requesterId,

            @PathVariable Long ownerId,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                collectionItemService.getMemberCollectionItems(
                        requesterId,
                        ownerId,
                        pageable
                )
        );
    }

    @GetMapping("/collection-items/{collectionItemId}")
    @Operation(summary = "도감 아이템 상세 조회")
    public ResponseEntity<CollectionItemResponse> getCollectionItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long collectionItemId
    ) {
        return ResponseEntity.ok(
                collectionItemService.getCollectionItem(
                        memberId,
                        collectionItemId
                )
        );
    }

    @PatchMapping(
            value = "/collection-items/{collectionItemId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "도감 아이템 수정")
    public ResponseEntity<CollectionItemResponse> updateCollectionItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long collectionItemId,

            @Valid @ModelAttribute
            CollectionItemUpdateRequest request
    ) {
        return ResponseEntity.ok(
                collectionItemService.updateCollectionItem(
                        memberId,
                        collectionItemId,
                        request
                )
        );
    }

    @DeleteMapping("/collection-items/{collectionItemId}")
    @Operation(summary = "도감 아이템 삭제")
    public ResponseEntity<Void> deleteCollectionItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long collectionItemId
    ) {
        collectionItemService.deleteCollectionItem(
                memberId,
                collectionItemId
        );

        return ResponseEntity.noContent().build();
    }
}