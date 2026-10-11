package com.anabada.fleaflea.global.security.oauth2.dto;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

public record CustomOidcUser(
        OidcUser delegate,
        OAuth2LoginStatus status,
        @Nullable Long memberId,
        OAuth2MemberInfo memberInfo
) implements OidcUser {

    public static CustomOidcUser registered(
            OidcUser delegate,
            Long memberId,
            OAuth2MemberInfo memberInfo
    ) {
        return new CustomOidcUser(
                delegate,
                OAuth2LoginStatus.REGISTERED,
                memberId,
                memberInfo
        );
    }

    public static CustomOidcUser signupRequired(
            OidcUser delegate,
            OAuth2MemberInfo memberInfo
    ) {
        return new CustomOidcUser(
                delegate,
                OAuth2LoginStatus.SIGNUP_REQUIRED,
                null,
                memberInfo
        );
    }

    public boolean isRegistered() {
        return status == OAuth2LoginStatus.REGISTERED;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public @Nullable OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return memberInfo.provider() + ":" + memberInfo.providerId();
    }
}
