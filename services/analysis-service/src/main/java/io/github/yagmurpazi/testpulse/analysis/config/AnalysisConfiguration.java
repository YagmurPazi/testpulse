package io.github.yagmurpazi.testpulse.analysis.config;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangePublisher;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.application.service.ChangeTestStatusService;
import io.github.yagmurpazi.testpulse.analysis.application.service.ProcessTestRunService;
import io.github.yagmurpazi.testpulse.analysis.application.service.StatusChangeRecorder;
import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import io.github.yagmurpazi.testpulse.analysis.domain.FlakinessDetector;
import io.github.yagmurpazi.testpulse.events.Topics;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the framework-free domain and use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalysisProperties.class)
class AnalysisConfiguration {

    /** Millisecond precision, so timestamps read back from PostgreSQL equal the ones that were written. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Bean
    DetectionSettings detectionSettings(AnalysisProperties properties) {
        return properties.detection().toSettings();
    }

    @Bean
    FlakinessDetector flakinessDetector(DetectionSettings settings) {
        return FlakinessDetector.withDefaultStrategies(settings);
    }

    @Bean
    StatusChangeRecorder statusChangeRecorder(StatusChangeLog log, StatusChangePublisher publisher) {
        return new StatusChangeRecorder(log, publisher);
    }

    @Bean
    ProcessTestRunUseCase processTestRunUseCase(ProcessedEvents processedEvents, MonitoredTestRepository tests,
            TestExecutionRepository executions, StatusChangeRecorder recorder, FlakinessDetector detector,
            DetectionSettings settings, PlatformTransactionManager transactionManager) {
        return TransactionalUseCases.processTestRun(
                new ProcessTestRunService(processedEvents, tests, executions, recorder, detector, settings,
                        UUID::randomUUID),
                new TransactionTemplate(transactionManager));
    }

    @Bean
    ChangeTestStatusUseCase changeTestStatusUseCase(MonitoredTestRepository tests, StatusChangeRecorder recorder,
            Clock clock, PlatformTransactionManager transactionManager) {
        return TransactionalUseCases.changeTestStatus(new ChangeTestStatusService(tests, recorder, clock),
                new TransactionTemplate(transactionManager));
    }

    /** Producer-owned topic. Status changes feed notifications; 30 days are enough to recover a consumer. */
    @Bean
    NewTopic testStatusChangesTopic(
            @Value("${testpulse.kafka.test-status-changes.partitions}") int partitions,
            @Value("${testpulse.kafka.test-status-changes.replication-factor}") short replicationFactor) {
        return TopicBuilder.name(Topics.TEST_STATUS_CHANGES)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }
}
