package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.domain.OAuth2Provider;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOAuth2User;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOidcUser;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailNotFoundException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailNotVerifiedException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2ProviderIdNotFoundException;
import com.anabada.fleaflea.global.security.oauth2.exception.UnsupportedOAuth2ProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {
    private final OAuth2PrincipalFactory principalFactory;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        if (!"google".equals(registrationId)) {
            throw new UnsupportedOAuth2ProviderException(registrationId);
        }

        String providerId = oidcUser.getSubject();
        String email = oidcUser.getEmail();

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2ProviderIdNotFoundException();
        }

        if (email == null || email.isBlank()) {
            throw new OAuth2EmailNotFoundException();
        }

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2EmailNotVerifiedException();
        }

        OAuth2MemberInfo memberInfo = new OAuth2MemberInfo(
                OAuth2Provider.GOOGLE,
                providerId,
                email,
                oidcUser.getFullName()
        );

        CustomOAuth2User principal = principalFactory.create(
                memberInfo,
                oidcUser.getAttributes()
        );

        return new CustomOidcUser(oidcUser, principal);
    }
}
