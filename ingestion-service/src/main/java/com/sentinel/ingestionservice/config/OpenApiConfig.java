package com.sentinel.ingestionservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger documentation configuration.
 *
 * Accessible at: http://localhost:8081/swagger-ui/index.html
 *
 * External fintechs integrating with Sentinel use this
 * page to understand the API — what endpoints exist,
 * what fields are required, what responses look like.
 * No separate documentation needed.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sentinelIngestionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel — Transaction Ingestion API")
                        .description(
                                "Real-time fraud detection platform. " +
                                        "Submit transactions for AI-powered risk assessment."
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sentinel Engineering")
                                .email("innehlemuelux@gmail.com")
                        )
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local development")
                ));
    }
}