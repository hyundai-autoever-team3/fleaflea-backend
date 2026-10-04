package com.anabada.fleaflea.global.security.oauth2;

import com.anabada.fleaflea.domain.member.domain.OAuth2Provider;
import com.anabada.fleaflea.global.security.oauth2.dto.OAuth2MemberInfo;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2AccountNotFoundException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailNotFoundException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2EmailNotVerifiedException;
import com.anabada.fleaflea.global.security.oauth2.exception.OAuth2ProviderIdNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KakaoUserInfoMapper {
    public OAuth2MemberInfo map(Map<String, Object> attributes) {
        String providerId = extractProviderId(attributes);
        Map<? ,?> account = extractAccount(attributes);

        String email = getString(account, "email");
        if (email == null || email.isBlank()) {
            throw new OAuth2EmailNotFoundException();
        }
        validateEmail(account);
        return new OAuth2MemberInfo(
                OAuth2Provider.KAKAO,
                providerId,
                email,
                extractNickname(account)
        );
    }

    private String extractProviderId(Map<String, Object> attributes) {
        Object id = attributes.get("id");

        if (id == null) {
            throw new OAuth2ProviderIdNotFoundException();
        }
        return String.valueOf(id);
    }

    private Map<?, ?> extractAccount(Map<String,Object> attributes) {
        Object account = attributes.get("kakao_account");

        if (!(account instanceof Map<?, ?> accountMap)) {
            throw new OAuth2AccountNotFoundException();
        }
        return accountMap;
    }

    private void validateEmail(Map<?, ?> account) {
        Object emailValid = account.get("is_email_valid");
        Object emailVerified = account.get("is_email_verified");

        if (Boolean.FALSE.equals(emailValid) || Boolean.FALSE.equals(emailVerified)) {
            throw new OAuth2EmailNotVerifiedException();
        }
    }

    private String extractNickname(Map<?, ?> account) {
        Object profile = account.get("profile");

        if (!(profile instanceof Map<?, ?> profileMap)) {
            return null;
        }

        return getString(profileMap, "nickname");
    }

    private String getString(Map<?, ?> values, String key) {
        Object value = values.get(key);

        return value instanceof String string
                ? string
                : null;
    }

}
