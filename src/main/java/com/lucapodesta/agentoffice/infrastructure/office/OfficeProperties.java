package com.lucapodesta.agentoffice.infrastructure.office;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled   expose the live event stream for the office view
 * @param heartbeat how often idle streams get a keep-alive comment
 */
@ConfigurationProperties("agent-office.office")
record OfficeProperties(@DefaultValue("true") boolean enabled, @DefaultValue("15s") Duration heartbeat) {
}
