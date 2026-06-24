package com.example.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI twoDayMovieOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("2DayMovie API")
                        .description("API de salons video prives avec upload MinIO, PIN et synchronisation temps reel.")
                        .version("v1")
                        .contact(new Contact()
                                .name("2DayMovie")
                                .url("https://example.local")));
    }
}
