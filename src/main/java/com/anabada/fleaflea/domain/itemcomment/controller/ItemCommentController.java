package com.anabada.fleaflea.domain.itemcomment.controller;

import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentCreateRequest;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentResponse;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentUpdateRequest;
import com.anabada.fleaflea.domain.itemcomment.service.ItemCommentService;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/items/{itemId}/comments")
@Tag(name = "상품 댓글")
@ApiResponse(responseCode = "401", description = "인증 필요",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "마켓 미참여 또는 다른 작성자의 댓글 변경",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "상품 또는 해당 상품의 댓글을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ItemCommentController {

    private final ItemCommentService commentService;

    @GetMapping
    @Operation(summary = "상품 댓글 조회", description = "마켓 참여자만 조회 가능. 오래된 순으로 조회. "
            + "parentId를 생략하면 일반 댓글, 지정하면 해당 댓글의 대댓글 조회. page는 0부터, size는 1~100")
    @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "페이지·크기·부모 댓글 값이 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PageResponse<ItemCommentResponse>> findComments(
            @PathVariable @Positive Long itemId,
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) @Positive Long parentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(commentService.findComments(itemId, memberId, parentId, page, size));
    }

    @PostMapping
    @Operation(summary = "상품 댓글·대댓글 작성", description = "마켓 참여자만 작성 가능. 대댓글은 1단계까지 허용")
    @ApiResponse(responseCode = "201", description = "댓글 작성 성공")
    @ApiResponse(responseCode = "400", description = "빈 내용·2000자 초과 또는 대댓글에 답글 작성",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ItemCommentResponse> createComment(
            @PathVariable @Positive Long itemId,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ItemCommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(itemId, memberId, request));
    }

    @PatchMapping("/{commentId}")
    @Operation(summary = "상품 댓글 수정", description = "마켓에 참여 중인 작성자만 수정 가능")
    @ApiResponse(responseCode = "200", description = "댓글 수정 성공")
    @ApiResponse(responseCode = "400", description = "빈 내용 또는 2000자 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ItemCommentResponse> updateComment(
            @PathVariable @Positive Long itemId,
            @PathVariable @Positive Long commentId,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ItemCommentUpdateRequest request
    ) {
        return ResponseEntity.ok(commentService.updateComment(itemId, commentId, memberId, request));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "상품 댓글 삭제", description = "마켓에 참여 중인 작성자만 삭제 가능. "
            + "부모 댓글 삭제 시 대댓글도 함께 삭제")
    @ApiResponse(responseCode = "204", description = "댓글 삭제 성공", content = @Content)
    public ResponseEntity<Void> deleteComment(
            @PathVariable @Positive Long itemId,
            @PathVariable @Positive Long commentId,
            @AuthenticationPrincipal Long memberId
    ) {
        commentService.deleteComment(itemId, commentId, memberId);
        return ResponseEntity.noContent().build();
    }
}
