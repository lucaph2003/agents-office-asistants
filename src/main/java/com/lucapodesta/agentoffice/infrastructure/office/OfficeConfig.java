package com.lucapodesta.agentoffice.infrastructure.office;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The live office feed exists unless {@code agent-office.office.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OfficeProperties.class)
@ConditionalOnBooleanProperty(name = "agent-office.office.enabled", matchIfMissing = true)
class OfficeConfig {

    @Bean
    OfficeStreamBroadcaster officeStreamBroadcaster(OfficeProperties properties) {
        return new OfficeStreamBroadcaster(properties.heartbeat());
    }

    @Bean
    OfficeStreamController officeStreamController(OfficeStreamBroadcaster broadcaster) {
        return new OfficeStreamController(broadcaster);
    }
}
