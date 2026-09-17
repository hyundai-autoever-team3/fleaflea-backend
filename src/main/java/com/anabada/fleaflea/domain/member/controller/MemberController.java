package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.dto.*;
import com.anabada.fleaflea.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "회원", description = "회원 프로필 조회, 수정 및 회원 탈퇴 API")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long memberId
    ) {
        MyProfileResponse response = memberService.getMyProfile(memberId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 프로필 수정",
            description = """
                    현재 로그인한 사용자의 프로필을 수정합니다.
                    닉네임과 프로필 이미지를 수정할 수 있으며,
                    변경하지 않는 값은 전송하지 않아도 됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "프로필 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 닉네임", content = @Content)
    })
    @PatchMapping(
            value = "/me",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> updateMyProfile(
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute ProfileUpdateRequest request
    ) {
        memberService.updateMyProfile(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 변경", description = "현재 로그인한 사용자의 비밀번호를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "현재 비밀번호가 일치하지 않거나 잘못된 요청", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content)
    })
    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        memberService.updatePassword(memberId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "회원 탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content)
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Long memberId
    ) {
        memberService.deleteMember(memberId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "닉네임으로 회원 검색", description = "닉네임을 기준으로 회원을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원 검색 성공"),
            @ApiResponse(responseCode = "400", description = "닉네임이 비어 있음"),
            @ApiResponse(responseCode = "404", description = "해당 회원을 찾을 수 없음")
    })
    @GetMapping("/search")
    public SearchMemberResponse searchMember(
            @Parameter(description = "검색할 회원의 닉네임", example = "홍길동", required = true)
            @RequestParam @NotBlank String nickname
    ) {
        return memberService.searchMember(nickname);
    }
}