package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    OpenAPI agentOfficeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Office API")
                        .version("v1")
                        .description("Turns goals into tasks that specialized AI agents execute and review."))
                .components(new Components().addSecuritySchemes(API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY));
    }

    @Bean
    OperationCustomizer correlationIdHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(CorrelationIdFilter.HEADER)
                .required(false)
                .description("Propagated to logs and echoed in the response. Generated when absent or malformed.")
                .schema(new StringSchema().maxLength(64)));
    }
}
