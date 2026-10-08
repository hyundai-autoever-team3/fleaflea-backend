package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.global.security.oauth2.dto.NaverProfileResponse;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2AccountNotFoundException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2UserInfoFetchFailedException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NaverOAuth2UserService extends DefaultOAuth2UserService {
    private final RestClient restClient;

    public NaverOAuth2UserService() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        if (!"naver".equals(registrationId)) {
            return super.loadUser(userRequest);
        }

        NaverProfileResponse body;

        try {
            body = restClient.get()
                    .uri(userRequest.getClientRegistration()
                            .getProviderDetails()
                            .getUserInfoEndpoint()
                            .getUri())
                    .headers(headers -> headers.setBearerAuth(
                            userRequest.getAccessToken().getTokenValue()
                    ))
                    .retrieve()
                    .body(NaverProfileResponse.class);
        } catch (RestClientException exception) {
            throw new OAuth2UserInfoFetchFailedException(exception);
        }

        if (body == null
                || !"00".equals(body.resultcode())
                || body.response() == null) {
            throw new OAuth2AccountNotFoundException();
        }

        Map<String, Object> attributes =
                new HashMap<>(body.response());

        Object id = attributes.remove("id");

        if (!(id instanceof String subject) || subject.isBlank()) {
            throw new OAuth2AccountNotFoundException();
        }

        attributes.put("sub", subject);

        return new DefaultOAuth2User(
                List.of(new OAuth2UserAuthority(attributes)),
                attributes,
                "sub"
        );
    }

}
