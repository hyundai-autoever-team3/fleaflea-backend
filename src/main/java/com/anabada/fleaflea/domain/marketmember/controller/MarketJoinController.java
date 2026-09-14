package com.anabada.fleaflea.domain.marketmember.controller;

import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinRequest;
import com.anabada.fleaflea.domain.marketmember.dto.MarketJoinResponse;
import com.anabada.fleaflea.domain.marketmember.service.MarketJoinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/market-joins")
@RequiredArgsConstructor
public class MarketJoinController {

    private final MarketJoinService marketJoinService;

    @PostMapping
    public ResponseEntity<MarketJoinResponse> joinMarket(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MarketJoinRequest request
    ) {
        MarketJoinResponse response = marketJoinService.joinMarket(
                Long.valueOf(memberId),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}