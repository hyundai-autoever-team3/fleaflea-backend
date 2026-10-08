package com.anabada.fleaflea.global.security.oauth2.dto;

import java.util.Map;

public record NaverProfileResponse(
            String resultcode,
            String message,
            Map<String, Object> response
    ) {}