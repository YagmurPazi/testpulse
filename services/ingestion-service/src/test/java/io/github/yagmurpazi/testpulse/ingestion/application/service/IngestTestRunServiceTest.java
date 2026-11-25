package io.github.yagmurpazi.testpulse.ingestion.application.service;

import static io.github.yagmurpazi.testpulse.ingestion.domain.TestData.COMMIT;
import static io.github.yagmurpazi.testpulse.ingestion.domain.TestData.IDENTITY;
import static io.github.yagmurpazi.testpulse.ingestion.domain.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestTestRunServiceTest {

    private static final UUID RUN_ID = UUID.fromString("0b8e7d6c-5a4f-4e3d-8c2b-1a0f9e8d7c6b");

    private final InMemoryTestRunRepository repository = new InMemoryTestRunRepository();
    private final RecordingEventPublisher publisher = new RecordingEventPublisher();
    private final IngestTestRunService service = new IngestTestRunService(
            repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC), () -> RUN_ID);

    @Test
    void storesNewRunAndPublishesOneEvent() {
        IngestionResult result = service.ingest(command(IDENTITY));

        assertThat(result.created()).isTrue();
        assertThat(result.run().id()).isEqualTo(RUN_ID);
        assertThat(result.run().ingestedAt()).isEqualTo(NOW);
        assertThat(result.run().commitSha()).isEqualTo(COMMIT);
        assertThat(result.run().branch()).isEqualTo("main");
        assertThat(result.run().results()).hasSize(2);
        assertThat(repository.findByIdentity(IDENTITY)).contains(result.run());
        assertThat(publisher.published()).containsExactly(result.run());
    }

    @Test
    void returnsStoredRunWithoutNewEventWhenIdentityIsIngestedAgain() {
        TestRun first = service.ingest(command(IDENTITY)).run();

        IngestionResult second = service.ingest(command(IDENTITY));

        assertThat(second.created()).isFalse();
        assertThat(second.run()).isEqualTo(first);
        assertThat(repository.size()).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    void treatsAnotherAttemptOfTheSameCiRunAsNewRun() {
        service.ingest(command(IDENTITY));
        RunIdentity rerun = new RunIdentity(IDENTITY.repository(), IDENTITY.externalRunId(), 2);

        IngestionResult result = service.ingest(command(rerun));

        assertThat(result.created()).isTrue();
        assertThat(publisher.published()).hasSize(2);
    }

    @Test
    void returnsWinnerWithoutEventWhenConcurrentRequestStoredTheSameRunFirst() {
        TestRun winner = TestData.run(IDENTITY, List.of(TestData.passed("a")));
        LosingRaceRepository racingRepository = new LosingRaceRepository(winner);
        IngestTestRunService racingService = new IngestTestRunService(
                racingRepository, publisher, Clock.fixed(NOW, ZoneOffset.UTC), () -> RUN_ID);

        IngestionResult result = racingService.ingest(command(IDENTITY));

        assertThat(result.created()).isFalse();
        assertThat(result.run()).isEqualTo(winner);
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void failsLoudlyWhenConflictingRunCannotBeFound() {
        LosingRaceRepository inconsistentRepository = new LosingRaceRepository(null);
        IngestTestRunService racingService = new IngestTestRunService(
                inconsistentRepository, publisher, Clock.fixed(NOW, ZoneOffset.UTC), () -> RUN_ID);

        assertThatIllegalStateException().isThrownBy(() -> racingService.ingest(command(IDENTITY)))
                .withMessageContaining("vanished");
        assertThat(publisher.published()).isEmpty();
    }

    private static IngestTestRunCommand command(RunIdentity identity) {
        return new IngestTestRunCommand(identity, COMMIT, "main",
                List.of(TestData.passed("a"), TestData.failed("b")));
    }

    /**
     * Simulates another request storing the run between our lookup and our insert.
     * A {@code null} winner simulates an inconsistent store that reports a conflict but cannot find the run.
     */
    private static final class LosingRaceRepository extends InMemoryTestRunRepository {

        private final TestRun winner;
        private boolean firstLookup = true;

        LosingRaceRepository(TestRun winner) {
            this.winner = winner;
        }

        @Override
        public java.util.Optional<TestRun> findByIdentity(RunIdentity identity) {
            if (firstLookup) {
                firstLookup = false;
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(winner);
        }

        @Override
        public boolean saveIfAbsent(TestRun run) {
            return false;
        }
    }
}
