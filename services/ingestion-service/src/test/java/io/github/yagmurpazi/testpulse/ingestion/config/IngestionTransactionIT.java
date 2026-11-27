package io.github.yagmurpazi.testpulse.ingestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.ingestion.IntegrationTest;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.application.service.IngestTestRunService;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The whole ingestion path against PostgreSQL: run, results and outbox event share one transaction. */
class IngestionTransactionIT extends IntegrationTest {

    @Autowired
    private IngestTestRunUseCase ingestTestRun;

    @Autowired
    private TestRunRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void storesRunAndWritesOneOutboxEventForIt() {
        IngestionResult result = ingestTestRun.ingest(command());

        assertThat(result.created()).isTrue();
        Map<String, Object> outbox = jdbc.sql("SELECT topic, message_key, payload, published_at FROM outbox_event")
                .query().singleRow();
        assertThat(outbox).containsEntry("topic", Topics.TEST_RUNS)
                .containsEntry("message_key", "YagmurPazi/testpulse")
                .containsEntry("published_at", null);
        TestRunIngested event = jsonMapper.readValue((String) outbox.get("payload"), TestRunIngested.class);
        assertThat(event.runId()).isEqualTo(result.run().id());
        assertThat(event.results()).hasSize(2);
    }

    @Test
    void ingestingTheSameRunAgainWritesNoSecondEvent() {
        IngestionResult first = ingestTestRun.ingest(command());

        IngestionResult second = ingestTestRun.ingest(command());

        assertThat(second.created()).isFalse();
        assertThat(second.run()).isEqualTo(first.run());
        assertThat(count("test_run")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void rollsBackTheRunWhenTheEventCannotBeWritten() {
        IngestTestRunUseCase failingPublisherUseCase = new TransactionalIngestTestRunUseCase(
                new IngestTestRunService(repository, run -> {
                    throw new IllegalStateException("outbox unavailable");
                }, Clock.systemUTC(), UUID::randomUUID),
                transaction);

        assertThatThrownBy(() -> failingPublisherUseCase.ingest(command()))
                .hasMessage("outbox unavailable");

        assertThat(count("test_run")).isZero();
        assertThat(count("test_case_result")).isZero();
    }

    private static IngestTestRunCommand command() {
        return new IngestTestRunCommand(TestData.IDENTITY, TestData.COMMIT, "main",
                List.of(TestData.passed("a"), TestData.failed("b")));
    }
}
