package com.anabada.fleaflea.domain.market.controller;

import com.anabada.fleaflea.domain.market.dto.MarketCreateRequest;
import com.anabada.fleaflea.domain.market.dto.MarketCreateResponse;
import com.anabada.fleaflea.domain.market.service.MarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @PostMapping
    public ResponseEntity<MarketCreateResponse> createMarket(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody MarketCreateRequest request
    ) {
        MarketCreateResponse response = marketService.createMarket(
                Long.valueOf(memberId),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}