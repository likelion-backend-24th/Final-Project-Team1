package com.team1.identity.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Identity-Service API")
                        .version("v1")
                        .description("""
                                회원가입 · 로그인 · 주최자 계정 발급.

                                인증: Authorization: Bearer <token>
                                Token 클레임은 sub에 userId(문자열), role에 Role 문자열을 담으며,
                                각 Service가 common-security로 직접 검증한다.

                                모든 응답은 공통 봉투를 사용한다.
                                성공 { "success": true, "data": ..., "meta": ..., "message": null }
                                실패 { "success": false, "data": { "code": "..." }, "meta": null, "message": "..." }
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
