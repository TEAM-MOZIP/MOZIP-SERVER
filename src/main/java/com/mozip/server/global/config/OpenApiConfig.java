package com.mozip.server.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("MOZIP API")
                .description("MOZIP 메인 백엔드 서버 API 문서")
                .version("v1");

        return new OpenAPI().info(info);
    }

}