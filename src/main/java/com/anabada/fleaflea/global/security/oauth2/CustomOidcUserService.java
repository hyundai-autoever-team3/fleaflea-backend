package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.domain.SocialProvider;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOidcUser;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.global.security.oauth2.exception.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService extends OidcUserService {
    private final MemberRepository memberRepository;

    public CustomOidcUserService(
            MemberRepository memberRepository,
            NaverOAuth2UserService naverOAuth2UserService
    ) {
        this.memberRepository = memberRepository;
        setOauth2UserService(naverOAuth2UserService);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        SocialProvider provider = resolveProvider(registrationId);
        String providerId = extractProviderId(oidcUser);

        return memberRepository
                .findBySocialProviderAndProviderId(provider, providerId)
                .map(member -> CustomOidcUser.registered(
                        oidcUser,
                        member.getMemberId(),
                        new OAuth2MemberInfo(
                                provider,
                                providerId,
                                member.getEmail(),
                                member.getNickname()
                        )
                ))
                .orElseGet(() -> createSignupPrincipal(
                        provider,
                        providerId,
                        oidcUser
                ));
    }

    private SocialProvider resolveProvider(String registrationId) {
        return switch (registrationId) {
            case "google" -> SocialProvider.GOOGLE;
            case "kakao" -> SocialProvider.KAKAO;
            case "naver" -> SocialProvider.NAVER;
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

    private CustomOidcUser createSignupPrincipal(
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
            case KAKAO, NAVER -> oidcUser.getClaimAsString("nickname");
            default -> throw new UnsupportedOAuth2ProviderException(provider.name());
        };

        if (memberRepository.existsByEmail(email)) {
            throw new OAuth2EmailAlreadyRegisteredException();
        }

        return CustomOidcUser.signupRequired(
                oidcUser,
                new OAuth2MemberInfo(
                        provider,
                        providerId,
                        email,
                        displayName
                )
        );

    }
}
