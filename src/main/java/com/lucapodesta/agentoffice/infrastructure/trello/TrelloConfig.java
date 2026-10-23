package com.lucapodesta.agentoffice.infrastructure.trello;

import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.application.service.BoardIntake;
import com.lucapodesta.agentoffice.application.service.TaskBoardSynchronizer;
import com.lucapodesta.agentoffice.application.service.TaskEventRecorder;
import com.lucapodesta.agentoffice.application.service.TaskService;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** The whole Trello integration only exists when {@code agent-office.trello.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrelloProperties.class)
@ConditionalOnBooleanProperty("agent-office.trello.enabled")
class TrelloConfig {

    @Bean
    TrelloTaskBoardAdapter trelloTaskBoardAdapter(RestClient.Builder builder, TrelloProperties properties) {
        properties.requireComplete();
        return new TrelloTaskBoardAdapter(restClient(builder, properties), properties.lists());
    }

    /**
     * Board updates happen after the triggering transaction committed, so their own writes need
     * a new transaction.
     */
    @Bean
    TaskBoardSynchronizer taskBoardSynchronizer(TrelloTaskBoardAdapter board, BoardCardRepository cards,
                                                TaskRepository tasks, AgentExecutionRepository executions,
                                                ReviewRepository reviews, TaskEventRecorder recorder,
                                                PlatformTransactionManager transactionManager, Clock clock) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new TaskBoardSynchronizer(board, cards, tasks, executions, reviews, recorder, requiresNew, clock);
    }

    @Bean
    BoardIntake boardIntake(TrelloTaskBoardAdapter board, BoardCardRepository cards, TaskService tasks) {
        return new BoardIntake(board, cards, tasks);
    }

    @Bean
    BoardSyncListener boardSyncListener(TaskBoardSynchronizer synchronizer) {
        return new BoardSyncListener(synchronizer);
    }

    static RestClient restClient(RestClient.Builder builder, TrelloProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "OAuth oauth_consumer_key=\"%s\", oauth_token=\"%s\""
                        .formatted(properties.apiKey(), properties.token()))
                .build();
    }
}
