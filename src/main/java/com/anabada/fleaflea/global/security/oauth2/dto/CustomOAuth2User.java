package com.anabada.fleaflea.global.security.oauth2.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record CustomOAuth2User(
        OAuth2LoginStatus status,
        Long memberId,
        OAuth2MemberInfo memberInfo,
        Map<String, Object> attributes
) implements OAuth2User {

    public static CustomOAuth2User registered(
            Long memberId,
            OAuth2MemberInfo memberInfo,
            Map<String, Object> attributes
    ) {
        return new CustomOAuth2User(
                OAuth2LoginStatus.REGISTERED,
                memberId,
                memberInfo,
                attributes
        );
    }

    public static CustomOAuth2User signupRequired(
            OAuth2MemberInfo memberInfo,
            Map<String, Object> attributes
    ) {
        return new CustomOAuth2User(
                OAuth2LoginStatus.SIGNUP_REQUIRED,
                null,
                memberInfo,
                attributes
        );
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean isRegistered() {
        return status == OAuth2LoginStatus.REGISTERED;
    }

    @Override
    public Collection<? extends GrantedAuthority>
    getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_USER")
        );
    }


    @Override
    public String getName() {
        return memberInfo.provider()
                + ":"
                + memberInfo.providerId();
    }
}
