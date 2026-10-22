package com.lucapodesta.agentoffice.infrastructure.notification;

import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.application.service.TaskEventRecorder;
import com.lucapodesta.agentoffice.application.service.TaskNotifier;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Notifications only exist when {@code agent-office.notifications.n8n.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(N8nNotificationProperties.class)
@ConditionalOnBooleanProperty("agent-office.notifications.n8n.enabled")
class NotificationConfig {

    @Bean
    N8nNotificationAdapter n8nNotificationAdapter(RestClient.Builder builder, N8nNotificationProperties properties) {
        properties.requireComplete();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader(N8nNotificationAdapter.TOKEN_HEADER, properties.token())
                .build();
        return new N8nNotificationAdapter(restClient, properties.webhookUrl());
    }

    @Bean
    TaskNotifier taskNotifier(N8nNotificationAdapter notifications, TaskRepository tasks,
                              AgentExecutionRepository executions, ReviewRepository reviews,
                              BoardCardRepository boardCards, TaskEventRecorder recorder,
                              PlatformTransactionManager transactionManager, Clock clock) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new TaskNotifier(notifications, tasks, executions, reviews, boardCards, recorder, requiresNew, clock);
    }

    @Bean
    NotificationListener notificationListener(TaskNotifier notifier) {
        return new NotificationListener(notifier);
    }
}
