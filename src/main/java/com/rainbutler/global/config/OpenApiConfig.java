package com.rainbutler.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정입니다.
 *
 * <p>JWT Bearer 보안 스키마는 정의만 해둡니다. 엔드포인트 적용은 인증 작업 때 합니다.
 */
@Configuration
public class OpenApiConfig {

    /** 보안 스키마 이름. 인증 작업 때 {@code @SecurityRequirement(name = ...)}로 참조합니다. */
    public static final String JWT_SCHEME_NAME = "bearerAuth";

    /**
     * OpenAPI 문서 기본 정보를 등록합니다.
     *
     * @return OpenAPI 설정
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("빗물받이 집사 API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
