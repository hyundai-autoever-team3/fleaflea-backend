package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.MyProfileResponse;
import com.anabada.fleaflea.domain.member.dto.PasswordUpdateRequest;
import com.anabada.fleaflea.domain.member.dto.ProfileUpdateRequest;
import com.anabada.fleaflea.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long memberId
    ) {
        MyProfileResponse response = memberService.getMyProfile(memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateMyProfile(
            @AuthenticationPrincipal Long memberId,
           @Valid @RequestBody ProfileUpdateRequest request
    ) {
        memberService.updateMyProfile(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        memberService.updatePassword(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Long memberId
    ) {
        memberService.deleteMember(memberId);

        return ResponseEntity.noContent().build();
    }
}
