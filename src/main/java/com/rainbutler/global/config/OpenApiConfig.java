package com.rainbutler.global.config;

import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정입니다.
 *
 * <p>모든 API에 JWT Bearer 인증을 기본으로 걸고, 인증이 필요 없는 API는
 * {@code @SecurityRequirements()}(빈 값)로 자물쇠를 뺍니다.
 */
@Configuration
public class OpenApiConfig {

    /** 보안 스키마 이름 */
    public static final String JWT_SCHEME_NAME = "bearerAuth";

    private static final String DESCRIPTION = """
            빗물받이 집사 백엔드 API

            **인증 방법**
            1. `/api/v1/auth/login` 또는 `/api/v1/auth/signup`으로 토큰을 발급받습니다.
            2. 오른쪽 위 **Authorize**에 `accessToken`만 붙여넣습니다. (`Bearer ` 생략)
            3. access 토큰이 만료되면(`EXPIRED_TOKEN`) `/api/v1/auth/refresh`로 재발급합니다.

            모든 응답은 `{ success, data, error }` 형식입니다.
            """;

    static {
        // 로그인 사용자 id 파라미터는 토큰에서 채우므로 문서에 노출하지 않는다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUserId.class);
    }

    /**
     * OpenAPI 문서 기본 정보와 보안 스키마를 등록합니다.
     *
     * @return OpenAPI 설정
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("빗물받이 집사 API")
                        .version("v1")
                        .description(DESCRIPTION))
                // Nginx 뒤 https에서도 Try it out이 같은 origin으로 호출하게 상대 경로를 쓴다
                .addServersItem(new Server().url("/"))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME_NAME));
    }
}
