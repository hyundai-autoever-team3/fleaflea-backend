package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOidcUser;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailAlreadyRegisteredException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuth2PrincipalFactory {

    private final MemberRepository memberRepository;

    public CustomOidcUser createSignupRequired(
            OAuth2MemberInfo memberInfo,
            OidcUser oidcUser
    ) {
        if (memberRepository.existsByEmail(memberInfo.email())) {
            throw new OAuth2EmailAlreadyRegisteredException();
        }

        return CustomOidcUser.signupRequired(oidcUser, memberInfo);
    }
}