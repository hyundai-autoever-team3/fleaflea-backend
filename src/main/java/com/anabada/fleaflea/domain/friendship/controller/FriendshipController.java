package com.anabada.fleaflea.domain.friendship.controller;

import com.anabada.fleaflea.domain.friendship.dto.FriendshipResponse;
import com.anabada.fleaflea.domain.friendship.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FriendshipController {
    private final FriendshipService friendshipService;

    @GetMapping("/members/me/friendship")
    public ResponseEntity<List<FriendshipResponse>> getReceivedRequests(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(friendshipService.getReceivedRequests(memberId));
    }
}
