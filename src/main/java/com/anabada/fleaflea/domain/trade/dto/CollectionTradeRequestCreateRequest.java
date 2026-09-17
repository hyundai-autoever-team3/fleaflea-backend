package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import jakarta.validation.constraints.NotNull;

public record CollectionTradeRequestCreateRequest(
        @NotNull(message = "거래 타입을 선택해주세요.")
        CollectionTradeType tradeType,
        
        Long offerCollectionItemId
) {}
