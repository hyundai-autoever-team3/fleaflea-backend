package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOAuth2User;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailAlreadyRegisteredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuth2PrincipalFactory {

    private final MemberRepository memberRepository;

    public CustomOAuth2User create(
            OAuth2MemberInfo memberInfo,
            Map<String, Object> attributes
    ) {
        return memberRepository
                .findByOauth2ProviderAndOauth2Id(
                        memberInfo.provider(),
                        memberInfo.providerId()
                )
                .map(member -> CustomOAuth2User.registered(
                        member.getMemberId(),
                        memberInfo,
                        attributes
                ))
                .orElseGet(() -> {
                    if (memberRepository.existsByEmail(memberInfo.email())) {
                        throw new OAuth2EmailAlreadyRegisteredException();
                    }

                    return CustomOAuth2User.signupRequired(
                            memberInfo,
                            attributes
                    );
                });
    }
}