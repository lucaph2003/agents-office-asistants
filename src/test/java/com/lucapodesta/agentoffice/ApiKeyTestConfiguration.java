package com.lucapodesta.agentoffice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/** Makes every MockMvc request carry the test API key, so tests focus on behaviour, not auth. */
@TestConfiguration(proxyBeanMethods = false)
public class ApiKeyTestConfiguration {

    public static final String API_KEY = "test-api-key-0123456789";

    @Bean
    MockMvcBuilderCustomizer apiKeyHeader() {
        return builder -> builder.defaultRequest(get("/").header("X-Api-Key", API_KEY));
    }
}
