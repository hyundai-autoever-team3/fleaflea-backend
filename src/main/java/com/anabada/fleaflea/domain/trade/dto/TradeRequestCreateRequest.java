package com.anabada.fleaflea.domain.trade.dto;

import java.time.LocalDate;

public record TradeRequestCreateRequest(
        Long swapItemId,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate
) {}
