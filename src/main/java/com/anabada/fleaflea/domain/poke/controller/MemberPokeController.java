package com.anabada.fleaflea.domain.poke.controller;

import com.anabada.fleaflea.domain.poke.dto.MemberPokeResponse;
import com.anabada.fleaflea.domain.poke.service.MemberPokeService;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "콕찌르기", description = "회원 간 콕찌르기 API")
public class MemberPokeController {

    private final MemberPokeService pokeService;

    @PostMapping("/members/{memberId}/pokes")
    @Operation(summary = "회원 콕찌르기", description = "상대 회원에게 콕찌르기를 보냅니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "콕찌르기 전송 성공"),
            @ApiResponse(responseCode = "400", description = "자기 자신에게 전송할 수 없음"),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음")
    })
    public ResponseEntity<Void> send(
            @AuthenticationPrincipal Long senderId,
            @PathVariable @Positive Long memberId
    ) {
        pokeService.send(senderId, memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members/me/pokes")
    @Operation(summary = "받은 콕찌르기 목록", description = "최신순으로 조회합니다. page는 0부터, size는 1~100입니다.")
    @ApiResponse(responseCode = "200", description = "목록 조회 성공")
    public ResponseEntity<PageResponse<MemberPokeResponse>> received(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(pokeService.received(memberId, page, size));
    }

    @PatchMapping("/pokes/{pokeId}/read")
    @Operation(summary = "콕찌르기 읽음 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "403", description = "받은 회원이 아님"),
            @ApiResponse(responseCode = "404", description = "콕찌르기를 찾을 수 없음")
    })
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable @Positive Long pokeId
    ) {
        pokeService.markRead(memberId, pokeId);
        return ResponseEntity.noContent().build();
    }
}
