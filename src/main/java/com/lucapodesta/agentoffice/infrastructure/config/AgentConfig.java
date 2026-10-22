package com.lucapodesta.agentoffice.infrastructure.config;

import com.lucapodesta.agentoffice.application.agent.AgentTeam;
import com.lucapodesta.agentoffice.application.agent.ManagerAgent;
import com.lucapodesta.agentoffice.application.agent.ReviewerAgent;
import com.lucapodesta.agentoffice.application.agent.WorkerAgent;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.application.service.TaskPipeline;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.review.ReviewPolicy;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PipelineProperties.class)
class AgentConfig {

    @Bean
    AgentTeam agentTeam(LanguageModelPort languageModel, PromptCatalog prompts) {
        return new AgentTeam(
                new ManagerAgent(languageModel, prompts),
                List.of(new WorkerAgent(AgentType.RESEARCH, languageModel, prompts),
                        new WorkerAgent(AgentType.DEVELOPER, languageModel, prompts)),
                new ReviewerAgent(languageModel, prompts));
    }

    @Bean
    TaskPipeline taskPipeline(TaskRepository tasks, AgentExecutionRepository executions, ReviewRepository reviews,
                              TaskEventRepository events, AgentTeam team, PipelineProperties properties,
                              TransactionTemplate transactionTemplate, Clock clock) {
        return new TaskPipeline(tasks, executions, reviews, events, team,
                new ReviewPolicy(properties.maxAttempts()), properties.maxOutputAttempts(),
                transactionTemplate, clock);
    }
}
