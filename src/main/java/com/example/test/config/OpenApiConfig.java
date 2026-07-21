package com.example.test.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wallet System API")
                .version("1.0.0")
                .description("""
                        A double-entry wallet supporting user onboarding, NUBAN account generation,
                        account funding and intra-institution fund transfers.

                        Money-moving endpoints accept an optional `Idempotency-Key` header. Retrying a
                        request with the same key returns the original outcome instead of moving money
                        twice. If a request times out and the outcome is unknown, resolve it with
                        `GET /api/v1/transfers/idempotency/{key}`: a 404 means the transfer did not
                        happen and is safe to retry.
                        """)
                .contact(new Contact().name("3Line Wallet Assessment"))
                .license(new License().name("Proprietary")));
    }
}
