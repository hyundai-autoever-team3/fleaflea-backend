package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.MyProfileResponse;
import com.anabada.fleaflea.domain.member.service.MemberMyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberMyPageController {
    private final MemberMyPageService memberMypageService;

    @GetMapping("/me")
    public MyProfileResponse getMyProfile(
            @AuthenticationPrincipal Long memberId
    ) {
        return memberMypageService.getMyProfile(memberId);
    }




}
