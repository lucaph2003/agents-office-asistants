package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI agentOfficeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Agent Office API")
                .version("v1")
                .description("Turns goals into tasks that specialized AI agents execute and review."));
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
