package com.acetelecom.repairdesk.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration (DOC-002).
 * UI available at: GET /api/v1/swagger-ui.html
 * JSON spec at:    GET /api/v1/v3/api-docs
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI repairDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RepairDesk API")
                        .version("1.0")
                        .description("RepairDesk V1 — repair shop management platform REST API")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
