package com.tickethub.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ticketHubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TicketHub API")
                .description("Продаж квитків на події з вибором місця в залі")
                .version("v1"));
    }
}
