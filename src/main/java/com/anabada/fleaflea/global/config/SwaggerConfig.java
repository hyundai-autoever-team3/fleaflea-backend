package com.anabada.fleaflea.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String ACCESS_TOKEN_SCHEME = "accessToken";

    @Bean
    public OpenAPI openAPI(
            @Value("${swagger.server-url}") String serverUrl
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title("FleaFlea API")
                        .description("""
                                FleaFlea API 문서

                                ### 카카오 · 구글 로그인 테스트

                                Swagger와 같은 브라우저의 새 탭에서 백엔드 주소에 다음 경로를 붙여 접속합니다.
                                아래 경로는 Spring Security가 처리하며 Swagger의 Execute로 호출하지 않습니다.

                                | 제공자 | 로그인 시작 경로 | 백엔드 콜백 경로 |
                                | --- | --- | --- |
                                | 카카오 | `/oauth2/authorization/kakao` | `/login/oauth2/code/kakao` |
                                | 구글(OIDC) | `/oauth2/authorization/google` | `/login/oauth2/code/google` |

                                로컬 백엔드 주소는 `http://localhost:8080`입니다.
                                제공자 콘솔에는 백엔드 주소와 콜백 경로를 합친 Redirect URI를 등록합니다.
                                콜백 주소는 직접 호출하지 않습니다.

                                로그인 후 프론트로 이동한 경로에 따라 Swagger에서 다음 API를 실행합니다.

                                | 이동 경로 | 다음 단계 |
                                | --- | --- |
                                | `/oauth/signup` | `POST /api/v1/auth/oauth2/signup`에 닉네임 제출 |
                                | `/oauth/success` | `POST /api/v1/auth/reissue`로 Access Token 발급 |
                                | `/oauth/failure?error=...` | 오류 코드 및 백엔드 로그인 실패 로그 확인 |

                                가입 티켓과 Refresh Token은 브라우저가 쿠키로 자동 전송합니다.
                                Swagger의 Servers를 로그인한 백엔드 주소와 동일하게 맞추세요.
                                로컬 프론트가 없어 연결 오류가 나더라도 쿠키가 발급됐다면 Swagger에서 계속 테스트할 수 있습니다.
                                쿠키가 누락되면 브라우저 개발자 도구에서 저장·전송 차단 사유를 확인하세요.

                                응답의 Access Token을 상단 Authorize에 `Bearer ` 없이 토큰 값만 입력한 뒤,
                                `GET /api/v1/members/me`로 인증을 확인합니다.
                                기존 일반 회원 또는 다른 소셜 계정과 이메일이 같아도 자동 연결하지 않으며 중복 오류로 처리합니다.
                                """)
                        .version("v1"))
                .addServersItem(new Server().url(serverUrl))
                .components(new Components()
                        .addSecuritySchemes(ACCESS_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(ACCESS_TOKEN_SCHEME));
    }

    @Bean
    public OpenApiCustomizer publicAuthOperations() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path : List.of(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/reissue",
                    "/api/v1/auth/oauth2/signup"
            )) {
                var item = openApi.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost().setSecurity(List.of());
                }
            }
        };
    }
}
