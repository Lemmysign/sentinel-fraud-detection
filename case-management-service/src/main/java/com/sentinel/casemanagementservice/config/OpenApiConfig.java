package com.sentinel.casemanagementservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger config for case management API.
 * Accessible at: http://localhost:8084/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI caseManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel — Case Management API")
                        .description(
                                "Fraud case review and analyst " +
                                        "decision management."
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sentinel Engineering")
                        )
                )
                .servers(List.of(new Server()
                        .url("http://localhost:8084")
                        .description("Local development")));
    }
}