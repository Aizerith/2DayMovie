package com.example.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI boilerplateOpenApi() {
        String bearerSchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("BoilerPlate Spring Angular API")
                        .description("Documentation OpenAPI du boilerplate avec auth JWT, roles et modules CRUD de reference.")
                        .version("v1")
                        .contact(new Contact()
                                .name("BoilerPlate Spring Angular")
                                .url("https://example.local")))
                .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
