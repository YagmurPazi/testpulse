package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TestRunIngestedMapperTest {

    private static final int LIMIT = io.github.yagmurpazi.testpulse.events.TestCaseResult.MAX_FAILURE_MESSAGE_LENGTH;

    @Test
    void mapsRunMetadataAndResults() {
        TestRun run = TestData.run(TestData.IDENTITY, List.of(TestData.passedOnRerun("a"), TestData.failed("b")));
        UUID eventId = UUID.randomUUID();

        List<TestRunIngested> events = TestRunIngestedMapper.toEvents(run, () -> eventId);

        assertThat(events).hasSize(1);
        TestRunIngested event = events.getFirst();
        assertThat(event.part()).isEqualTo(1);
        assertThat(event.parts()).isEqualTo(1);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.schemaVersion()).isEqualTo(TestRunIngested.SCHEMA_VERSION);
        assertThat(event.occurredAt()).isEqualTo(run.ingestedAt());
        assertThat(event.runId()).isEqualTo(run.id());
        assertThat(event.repository()).isEqualTo("YagmurPazi/testpulse");
        assertThat(event.commitSha()).isEqualTo(run.commitSha().value());
        assertThat(event.branch()).isEqualTo("main");
        assertThat(event.externalRunId()).isEqualTo("45619835962");
        assertThat(event.attempt()).isEqualTo(1);
        assertThat(event.results()).extracting(
                io.github.yagmurpazi.testpulse.events.TestCaseResult::testName,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::outcome,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::durationMillis,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::rerunFailures)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", TestOutcome.PASSED, 5L, 2),
                        org.assertj.core.groups.Tuple.tuple("b", TestOutcome.FAILED, 5L, 0));
        assertThat(event.results().get(1).failureMessage()).isEqualTo("expected: <1> but was: <2>");
    }

    @Test
    void keepsMessagesUpToTheLimit() {
        String atLimit = "x".repeat(LIMIT);

        assertThat(TestRunIngestedMapper.truncate(atLimit)).isSameAs(atLimit);
        assertThat(TestRunIngestedMapper.truncate(null)).isNull();
    }

    @Test
    void truncatesLongerMessagesToTheLimitWithMarker() {
        String truncated = TestRunIngestedMapper.truncate("x".repeat(LIMIT + 1));

        assertThat(truncated).hasSize(LIMIT).endsWith("…");
    }

    @Nested
    class SplittingLargeRuns {

        /** Kafka's default max.request.size. */
        private static final int KAFKA_MESSAGE_LIMIT = 1024 * 1024;

        private final JsonMapper json = JsonMapper.builder().build();

        @Test
        void splitsARunTheSizeOfALargeOpenSourceProjectIntoMessagesKafkaAccepts() {
            // apache/cassandra-java-driver: 5912 results per configuration, long class and test names
            TestRun run = TestData.run(TestData.IDENTITY, IntStream.range(0, 5912)
                    .mapToObj(i -> result("com.datastax.oss.driver.api.core.cql.continuous.reactive.Class" + i / 20,
                            "should_handle_reactive_continuous_paging_with_case_" + i, i % 50 == 0))
                    .toList());

            List<TestRunIngested> events = TestRunIngestedMapper.toEvents(run, UUID::randomUUID);

            assertThat(events).hasSizeGreaterThan(1);
            assertThat(events).allSatisfy(event ->
                    assertThat(jsonBytes(event)).isLessThan(TestRunIngestedMapper.MAX_PART_BYTES + 4096));
            assertThat(events).allSatisfy(event -> assertThat(jsonBytes(event)).isLessThan(KAFKA_MESSAGE_LIMIT));
            assertThat(events.stream().mapToInt(event -> event.results().size()).sum()).isEqualTo(5912);
        }

        @Test
        void numbersThePartsAndRepeatsTheRunInEach() {
            TestRun run = TestData.run(TestData.IDENTITY, IntStream.range(0, 300)
                    .mapToObj(i -> result("com.acme.CartTest", "test" + i, false)).toList());

            List<TestRunIngested> events = TestRunIngestedMapper.toEvents(run, UUID::randomUUID, 10_000);

            assertThat(events).extracting(TestRunIngested::part)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, events.size()).boxed().toList());
            assertThat(events).allSatisfy(event -> {
                assertThat(event.parts()).isEqualTo(events.size());
                assertThat(event.runId()).isEqualTo(run.id());
                assertThat(event.externalRunId()).isEqualTo("45619835962");
                assertThat(event.occurredAt()).isEqualTo(run.ingestedAt());
            });
            assertThat(new HashSet<>(events.stream().map(TestRunIngested::eventId).toList())).hasSize(events.size());
            assertThat(events.stream().flatMap(event -> event.results().stream())
                    .map(io.github.yagmurpazi.testpulse.events.TestCaseResult::testName).toList())
                    .containsExactlyElementsOf(IntStream.range(0, 300).mapToObj(i -> "test" + i).toList());
        }

        @Test
        void keepsAllResultsOfOneTestInTheSamePart() {
            List<io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult> results = new ArrayList<>();
            results.add(result("com.acme.CartTest", "repeated", true));
            IntStream.range(0, 200).forEach(i -> results.add(result("com.acme.CartTest", "test" + i, false)));
            results.add(result("com.acme.CartTest", "repeated", false));

            List<TestRunIngested> events = TestRunIngestedMapper.toEvents(
                    TestData.run(TestData.IDENTITY, results), UUID::randomUUID, 5_000);

            assertThat(events).hasSizeGreaterThan(1);
            assertThat(events).filteredOn(event -> event.results().stream()
                    .anyMatch(result -> result.testName().equals("repeated")))
                    .singleElement()
                    .satisfies(event -> assertThat(event.results())
                            .filteredOn(result -> result.testName().equals("repeated")).hasSize(2));
        }

        @Test
        void givesATestLargerThanTheLimitAPartOfItsOwn() {
            String huge = "x".repeat(3_000);
            TestRun run = TestData.run(TestData.IDENTITY, List.of(
                    result("com.acme.A", "small", false), result("com.acme.B", huge, false),
                    result("com.acme.C", "small", false)));

            List<TestRunIngested> events = TestRunIngestedMapper.toEvents(run, UUID::randomUUID, 1_000);

            assertThat(events).extracting(event -> event.results().size()).containsExactly(1, 1, 1);
        }

        @Test
        void estimatesNoLessThanTheActualJson() {
            io.github.yagmurpazi.testpulse.events.TestCaseResult result =
                    new io.github.yagmurpazi.testpulse.events.TestCaseResult("com.acme.ÇartTest", "tëst \"quoted\"",
                            io.github.yagmurpazi.testpulse.events.TestOutcome.FAILED, 123_456_789, 3,
                            "line one\nline \"two\"\twith tab");

            assertThat(TestRunIngestedMapper.estimatedJsonBytes(result))
                    .isGreaterThanOrEqualTo(json.writeValueAsString(result).getBytes(StandardCharsets.UTF_8).length);
        }

        private int jsonBytes(TestRunIngested event) {
            return json.writeValueAsString(event).getBytes(StandardCharsets.UTF_8).length;
        }
    }

    private static io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult result(String className,
            String testName, boolean failed) {
        return TestData.result(className, testName, failed);
    }
}
