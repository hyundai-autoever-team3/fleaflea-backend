package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.MyProfileResponse;
import com.anabada.fleaflea.domain.member.dto.PasswordUpdateRequest;
import com.anabada.fleaflea.domain.member.dto.ProfileUpdateRequest;
import com.anabada.fleaflea.domain.member.service.MemberMyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberMyPageController {
    private final MemberMyPageService memberMypageService;

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long memberId
    ) {
        MyProfileResponse response = memberMypageService.getMyProfile(memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateMyProfile(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ProfileUpdateRequest request
    ) {
        memberMypageService.updateMyProfile(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal Long memberId,
            @RequestBody PasswordUpdateRequest request
    ) {
        memberMypageService.updatePassword(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Long memberId
    ) {
        memberMypageService.deleteMember(memberId);

        return ResponseEntity.noContent().build();
    }
}
