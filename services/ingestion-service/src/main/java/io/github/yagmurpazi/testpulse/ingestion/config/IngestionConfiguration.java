package io.github.yagmurpazi.testpulse.ingestion.config;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.ReportParser;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunEventPublisher;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.application.service.IngestTestRunService;
import io.github.yagmurpazi.testpulse.ingestion.application.service.UploadReportsService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the framework-free use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class IngestionConfiguration {

    /** Millisecond precision, so timestamps read back from PostgreSQL equal the ones that were written. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Bean
    IngestTestRunUseCase ingestTestRunUseCase(TestRunRepository repository, TestRunEventPublisher eventPublisher,
            Clock clock, PlatformTransactionManager transactionManager) {
        IngestTestRunService service = new IngestTestRunService(repository, eventPublisher, clock, UUID::randomUUID);
        return new TransactionalIngestTestRunUseCase(service, new TransactionTemplate(transactionManager));
    }

    @Bean
    UploadReportsUseCase uploadReportsUseCase(ReportParser parser, IngestTestRunUseCase ingestTestRunUseCase) {
        return new UploadReportsService(parser, ingestTestRunUseCase);
    }
}
