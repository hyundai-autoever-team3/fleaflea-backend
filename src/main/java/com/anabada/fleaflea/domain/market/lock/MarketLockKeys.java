package com.anabada.fleaflea.domain.market.lock;

import com.anabada.fleaflea.domain.market.exception.InvalidMarketInviteCodeException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketLockKeys {

    private final MarketRepository marketRepository;

    public String getMarketKeyByInviteCode(String inviteCode) {
        Long marketId = marketRepository.findMarketIdByInviteCode(inviteCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(InvalidMarketInviteCodeException::new);
        return "market:" + marketId;
    }
}
