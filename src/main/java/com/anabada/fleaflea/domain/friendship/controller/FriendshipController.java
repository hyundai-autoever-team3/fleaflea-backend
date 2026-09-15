package com.anabada.fleaflea.domain.friendship.controller;

import com.anabada.fleaflea.domain.friendship.domain.FriendRequestDirection;
import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.exception.InvalidFriendRequestDirectionException;
import com.anabada.fleaflea.domain.friendship.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FriendshipController {
    private final FriendshipService friendshipService;

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

    @GetMapping("/members/me/friendship")
    public ResponseEntity<List<FriendshipResponse>> getMyFriends(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(friendshipService.getMyFriends(memberId));
    }

    @PostMapping("/members/{memberId}/friend-requests")
    public ResponseEntity<Void> requestFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("memberId") Long targetMemberId
    ) {
        friendshipService.requestFollow(memberId, targetMemberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/friend-requests/{requesterId}/accept")
    public ResponseEntity<Void> acceptFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long requesterId
    ) {
        friendshipService.acceptFollow(memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/friend-requests/{requesterId}/reject")
    public ResponseEntity<Void> rejectFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long requesterId
    ) {
        friendshipService.rejectFollow(memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/friend-requests/{requesterId}/cancel")
    public ResponseEntity<Void> cancelFollow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long requesterId
    ) {
        friendshipService.cancelFollow(memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/friendships/{friendshipId}")
    public ResponseEntity<Void> deleteFriend(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long friendshipId
    ) {
        friendshipService.deleteFriend(memberId, friendshipId);
        return ResponseEntity.noContent().build();
    }
}
