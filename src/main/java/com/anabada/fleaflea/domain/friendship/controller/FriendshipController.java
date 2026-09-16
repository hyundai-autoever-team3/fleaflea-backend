package com.anabada.fleaflea.domain.friendship.controller;

import com.anabada.fleaflea.domain.friendship.domain.FriendRequestDirection;
import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.exception.InvalidFriendRequestDirectionException;
import com.anabada.fleaflea.domain.friendship.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "친구 관계")
public class FriendshipController {
    private final FriendshipService friendshipService;


    @Operation(
            summary = "친구 요청 목록 조회",
            description = "SENT는 내가 보낸 친구 요청, RECEIVED는 내가 받은 친구 요청을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "친구 요청 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendshipResponse.class))),
            @ApiResponse(responseCode = "400", description = "친구 요청 방향 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/friend-requests")
    public ResponseEntity<List<FriendshipResponse>> getFriendRequest(
            @AuthenticationPrincipal Long memberId,
            @RequestParam FriendRequestDirection direction
    ) {
        if (direction == FriendRequestDirection.SENT) {
            return ResponseEntity.ok(friendshipService.getSentRequests(memberId));

        }
        if (direction == FriendRequestDirection.RECEIVED) {
            return ResponseEntity.ok(friendshipService.getReceivedRequests(memberId));
        }
        throw new InvalidFriendRequestDirectionException();
    }

    @Operation(summary = "내 친구 목록 조회", description = "현재 로그인한 사용자의 친구 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "친구 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = FriendshipResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me/friendship")
    public ResponseEntity<List<FriendshipResponse>> getMyFriends(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(friendshipService.getMyFriends(memberId));
    }

    @Operation(summary = "친구 요청 보내기", description = "특정 회원에게 친구 요청을 보냅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "친구 요청 성공"),
            @ApiResponse(responseCode = "400", description = "자기 자신에게 친구 요청을 보낼 수 없음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 친구 요청을 보냈거나 친구 관계가 존재함")
    })
    @PostMapping("/members/{memberId}/friend-requests")
    public ResponseEntity<Void> requestFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("memberId") Long targetMemberId
    ) {
        friendshipService.requestFollow(memberId, targetMemberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "친구 요청 수락", description = "현재 로그인한 사용자가 받은 친구 요청을 수락합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "친구 요청 수락 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "친구 요청을 찾을 수 없음")
    })
    @PostMapping("/friend-requests/{requesterId}/accept")
    public ResponseEntity<Void> acceptFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long requesterId
    ) {
        friendshipService.acceptFollow(memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "친구 요청 거절", description = "현재 로그인한 사용자가 받은 친구 요청을 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "친구 요청 거절 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "친구 요청을 찾을 수 없음")
    })
    @PostMapping("/friend-requests/{requesterId}/reject")
    public ResponseEntity<Void> rejectFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long requesterId
    ) {
        friendshipService.rejectFollow(memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "친구 요청 취소", description = "현재 로그인한 사용자가 보낸 친구 요청을 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "친구 요청 취소 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "친구 요청을 찾을 수 없음")
    })
    @PostMapping("/friend-requests/{addresseeId}/cancel")
    public ResponseEntity<Void> cancelFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long addresseeId
    ) {
        friendshipService.cancelFollow(memberId, addresseeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "친구 삭제", description = "현재 로그인한 사용자의 친구 관계를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "친구 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "친구 관계를 찾을 수 없음")
    })
    @DeleteMapping("/friendships/{friendshipId}")
    public ResponseEntity<Void> deleteFriend(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long friendshipId
    ) {
        friendshipService.deleteFriend(memberId, friendshipId);
        return ResponseEntity.noContent().build();
    }
}
