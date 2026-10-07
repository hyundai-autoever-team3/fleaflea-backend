package com.anabada.fleaflea.domain.member.controller;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.LoginRequest;
import com.anabada.fleaflea.domain.member.dto.TokenPair;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Import(RedisTestContainerConfiguration.class)
class MemberAuthIntegrationTest {

    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RedisRefreshTokenRepository redisRefreshTokenRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private final List<Long> memberIds = new ArrayList<>();
    private Member member;

    @BeforeEach
    void setUp() {
        member = createMember("auth");
    }

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            redisRefreshTokenRepository.deleteAllSessions(memberId);
            memberRepository.deleteById(memberId);
        }
    }

    @Test
    @DisplayName("여러 기기의 로그인이 독립적인 세션과 재발급을 유지한다")
    void login_fromMultipleDevices_preservesEachSession() throws Exception {
        TokenPair firstTokenPair = login(member);
        TokenPair secondTokenPair = login(member);

        assertThat(firstTokenPair.refreshToken()).isNotEqualTo(secondTokenPair.refreshToken());
        assertReissueSuccess(firstTokenPair);
        assertReissueSuccess(secondTokenPair);

        UUID sessionId = UUID.fromString(jwtTokenProvider.getClaims(firstTokenPair.refreshToken()).getId());
        assertThat(redisRefreshTokenRepository.findTokenHash(member.getMemberId(), sessionId))
                .hasValueSatisfying(tokenHash -> assertThat(tokenHash)
                        .matches("[0-9a-f]{64}").isNotEqualTo(firstTokenPair.refreshToken()));
    }

    @Test
    @DisplayName("현재 기기 로그아웃은 해당 세션만 제거하고 쿠키를 만료시킨다")
    void logout_withCurrentCookie_preservesOtherDevice() throws Exception {
        TokenPair firstTokenPair = login(member);
        TokenPair secondTokenPair = login(member);

        MvcResult mvcResult = mockMvc.perform(delete("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstTokenPair.accessToken())
                        .cookie(new Cookie("refresh_token", firstTokenPair.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Max-Age=0", "HttpOnly", "Secure", "Path=/api/v1/auth", "SameSite=None");
        assertReissueRejected(firstTokenPair);
        assertReissueSuccess(secondTokenPair);
    }

    @Test
    @DisplayName("전체 로그아웃은 자신의 모든 세션만 제거한다")
    void logoutAll_withAuthenticatedMember_preservesAnotherMember() throws Exception {
        TokenPair firstTokenPair = login(member);
        TokenPair secondTokenPair = login(member);
        TokenPair otherTokenPair = login(createMember("other"));

        mockMvc.perform(delete("/api/v1/auth/logout/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstTokenPair.accessToken()))
                .andExpect(status().isNoContent());

        assertReissueRejected(firstTokenPair);
        assertReissueRejected(secondTokenPair);
        assertReissueSuccess(otherTokenPair);
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 다른 기기의 세션을 삭제하지 않는다")
    void logout_withoutCookie_preservesExistingSessions() throws Exception {
        TokenPair tokenPair = login(member);

        mockMvc.perform(delete("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isNoContent());

        assertReissueSuccess(tokenPair);
    }

    @Test
    @DisplayName("다른 회원의 쿠키로 로그아웃할 수 없다")
    void logout_withAnotherMembersCookie_returnsUnauthorized() throws Exception {
        TokenPair tokenPair = login(member);
        TokenPair otherTokenPair = login(createMember("other"));

        mockMvc.perform(delete("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken())
                        .cookie(new Cookie("refresh_token", otherTokenPair.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        assertReissueSuccess(otherTokenPair);
    }

    @Test
    @DisplayName("쿠키 누락과 잘못된 토큰은 재발급할 수 없다")
    void reissue_withoutValidRefreshToken_returnsUnauthorized() throws Exception {
        TokenPair tokenPair = login(member);

        mockMvc.perform(post("/api/v1/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refresh_token", "invalid")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refresh_token", tokenPair.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("삭제된 회원의 세션으로 Access Token을 재발급하지 않는다")
    void reissue_afterMemberDeletion_returnsUnauthorized() throws Exception {
        TokenPair tokenPair = login(member);
        memberRepository.deleteById(member.getMemberId());

        assertReissueRejected(tokenPair);
    }

    @Test
    @DisplayName("로그아웃 API는 인증 없이 사용할 수 없다")
    void logout_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/auth/logout/all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Redis에 저장된 원문 해시가 바뀌면 재발급하지 않는다")
    void reissue_withChangedHash_returnsUnauthorized() throws Exception {
        TokenPair tokenPair = login(member);
        UUID sessionId = UUID.fromString(jwtTokenProvider.getClaims(tokenPair.refreshToken()).getId());
        redisRefreshTokenRepository.saveSession(
                member.getMemberId(), sessionId, "different-hash",
                jwtTokenProvider.getExpiration(tokenPair.refreshToken()).toInstant()
        );

        assertReissueRejected(tokenPair);
    }

    @Test
    @DisplayName("세션별 TTL은 다른 기기의 세션 만료시간에 영향을 주지 않는다")
    void expireSession_withDifferentLifetimes_preservesLongerSession() throws Exception {
        UUID shortSessionId = UUID.randomUUID();
        UUID longSessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(2);
        assertThat(redisRefreshTokenRepository.saveSession(
                member.getMemberId(), shortSessionId, "short-session", expiresAt
        )).isTrue();
        assertThat(redisRefreshTokenRepository.saveSession(
                member.getMemberId(), longSessionId, "long-session", Instant.now().plusSeconds(60)
        )).isTrue();

        Thread.sleep(2_200);

        assertThat(redisRefreshTokenRepository.findTokenHash(member.getMemberId(), shortSessionId)).isEmpty();
        assertThat(redisRefreshTokenRepository.findTokenHash(member.getMemberId(), longSessionId))
                .contains("long-session");
        redisRefreshTokenRepository.deleteSession(member.getMemberId(), longSessionId);
        assertThat(stringRedisTemplate.hasKey("auth:refresh:sessions:{" + member.getMemberId() + "}"))
                .isFalse();
    }

    @Test
    @DisplayName("이미 만료된 세션은 Redis에 남지 않는다")
    void saveSession_withPastExpiration_doesNotLeaveSession() {
        UUID sessionId = UUID.randomUUID();

        assertThat(redisRefreshTokenRepository.saveSession(
                member.getMemberId(), sessionId, "expired-session", Instant.now().minusSeconds(1)
        )).isFalse();
        assertThat(redisRefreshTokenRepository.findTokenHash(member.getMemberId(), sessionId)).isEmpty();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:sessions:{" + member.getMemberId() + "}"))
                .isFalse();
    }

    private Member createMember(String prefix) {
        Member createdMember = MemberFixture.createMember(prefix);
        createdMember.updatePassword(passwordEncoder.encode(PASSWORD));
        Member savedMember = memberRepository.save(createdMember);
        memberIds.add(savedMember.getMemberId());
        return savedMember;
    }

    private TokenPair login(Member loginMember) throws Exception {
        LoginRequest loginRequest = new LoginRequest(loginMember.getEmail(), PASSWORD);
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = mvcResult.getResponse().getCookie("refresh_token");
        assertThat(refreshTokenCookie).isNotNull();
        assertThat(refreshTokenCookie.isHttpOnly()).isTrue();
        assertThat(refreshTokenCookie.getSecure()).isTrue();
        String accessToken = jsonMapper.readTree(mvcResult.getResponse().getContentAsString())
                .get("accessToken").asString();
        return new TokenPair(accessToken, refreshTokenCookie.getValue());
    }

    private void assertReissueSuccess(TokenPair tokenPair) throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refresh_token", tokenPair.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private void assertReissueRejected(TokenPair tokenPair) throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refresh_token", tokenPair.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
