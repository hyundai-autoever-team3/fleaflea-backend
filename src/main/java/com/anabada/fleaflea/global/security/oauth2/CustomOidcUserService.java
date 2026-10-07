package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.domain.SocialProvider;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        SocialProvider provider = resolveProvider(registrationId);
        String providerId = extractProviderId(oidcUser);

        CustomOAuth2User principal = memberRepository
                .findBySocialProviderAndProviderId(
                        provider,
                        providerId
                )
                .map(member -> CustomOAuth2User.registered(
                        member.getMemberId(),
                        new OAuth2MemberInfo(
                                provider,
                                providerId,
                                member.getEmail(),
                                member.getNickname()
                        ),
                        oidcUser.getAttributes()
                ))
                .orElseGet(() -> createSignupPrincipal(
                        provider,
                        providerId,
                        oidcUser
                ));

        return new CustomOidcUser(oidcUser, principal);
    }

    private SocialProvider resolveProvider(String registrationId) {
        return switch (registrationId) {
            case "google" -> SocialProvider.GOOGLE;
            case "kakao" -> SocialProvider.KAKAO;
            default -> throw new UnsupportedOAuth2ProviderException(registrationId);
        };
    }

    private String extractProviderId(OidcUser oidcUser) {
        String providerId = oidcUser.getSubject();

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2ProviderIdNotFoundException();
        }
        return providerId;
    }

    private CustomOAuth2User createSignupPrincipal(
            SocialProvider provider,
            String providerId,
            OidcUser oidcUser
    ) {
        String email = oidcUser.getEmail();

        if (email == null || email.isBlank()) {
            throw new OAuth2EmailNotFoundException();
        }

        if (provider == SocialProvider.GOOGLE
                && !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2EmailNotVerifiedException();
        }

        String displayName = switch (provider) {
            case GOOGLE -> oidcUser.getFullName();
            case KAKAO -> oidcUser.getClaimAsString("nickname");
            default -> throw new UnsupportedOAuth2ProviderException(provider.name());
        };

        OAuth2MemberInfo memberInfo = new OAuth2MemberInfo(
                provider,
                providerId,
                email,
                displayName
        );

        return principalFactory.createSignupRequired(
                memberInfo,
                oidcUser.getAttributes()
        );

    }
}
