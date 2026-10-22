package com.lucapodesta.agentoffice.infrastructure.ollama;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaProperties.class)
class OllamaConfig {

    @Bean
    OllamaLanguageModelAdapter ollamaLanguageModelAdapter(RestClient.Builder builder, OllamaProperties properties,
                                                          MeterRegistry meters) {
        return new OllamaLanguageModelAdapter(ollamaClient(builder, properties), properties, meters);
    }

    static OllamaClient ollamaClient(RestClient.Builder builder, OllamaProperties properties) {
        // Ollama speaks plain HTTP/1.1; skipping the h2c upgrade attempt avoids stream resets.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new OllamaClient(restClient, properties.maxAttempts(), properties.retryBackoff());
    }
}
