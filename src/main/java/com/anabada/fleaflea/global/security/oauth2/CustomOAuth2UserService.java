package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.security.oauth2.dto.CustomOAuth2User;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailAlreadyRegisteredException;
import com.anabada.fleaflea.global.security.oauth2.exception.UnsupportedOAuth2ProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final KakaoUserInfoMapper kakaoUserInfoMapper;
    private final OAuth2PrincipalFactory principalFactory;

    @Override
    public OAuth2User loadUser(
            OAuth2UserRequest userRequest
    ) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User =
                super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        OAuth2MemberInfo memberInfo =
                mapUserInfo(
                        registrationId,
                        oAuth2User
                );

        return principalFactory.create(
                memberInfo,
                oAuth2User.getAttributes()
        );

    }



    private OAuth2MemberInfo mapUserInfo(
            String registrationId,
            OAuth2User oAuth2User
    ) {
        return switch (registrationId) {
            case "kakao" -> kakaoUserInfoMapper.map(
                    oAuth2User.getAttributes()
            );
            default -> throw new UnsupportedOAuth2ProviderException(registrationId);
        };
    }


}
