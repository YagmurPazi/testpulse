package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import io.github.yagmurpazi.testpulse.events.TestCaseResult;
import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Translates the domain model into the published event contract, one event per part of the run.
 *
 * <p>Kafka refuses messages above 1 MB ({@code max.request.size}), and a refused event would stay in the outbox: a run
 * of a few thousand tests already exceeds that. Runs are therefore split into parts of at most
 * {@link #MAX_PART_BYTES} of results, and all results of one test go into the same part, because analysis-service
 * records one execution per test and run.
 */
final class TestRunIngestedMapper {

    /** Half of Kafka's default message limit: room for the metadata, headers and an imprecise estimate. */
    static final int MAX_PART_BYTES = 512 * 1024;

    /** Field names, quotes, outcome and numbers of one result in JSON, generously. */
    private static final int RESULT_OVERHEAD_BYTES = 128;

    private static final String TRUNCATION_MARKER = "…";

    private TestRunIngestedMapper() {
    }

    static List<TestRunIngested> toEvents(TestRun run, Supplier<UUID> eventIds) {
        return toEvents(run, eventIds, MAX_PART_BYTES);
    }

    static List<TestRunIngested> toEvents(TestRun run, Supplier<UUID> eventIds, int maxPartBytes) {
        List<List<TestCaseResult>> parts = split(run.results().stream().map(TestRunIngestedMapper::toEvent).toList(),
                maxPartBytes);
        List<TestRunIngested> events = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            events.add(new TestRunIngested(
                    eventIds.get(),
                    TestRunIngested.SCHEMA_VERSION,
                    run.ingestedAt(),
                    run.id(),
                    run.identity().repository(),
                    run.commitSha().value(),
                    run.branch(),
                    run.identity().externalRunId(),
                    run.identity().attempt(),
                    parts.get(i),
                    i + 1,
                    parts.size()));
        }
        return events;
    }

    /** Fills parts in order, test by test; a single test larger than the limit gets a part of its own. */
    private static List<List<TestCaseResult>> split(List<TestCaseResult> results, int maxPartBytes) {
        Map<String, List<TestCaseResult>> byTest = new LinkedHashMap<>();
        for (TestCaseResult result : results) {
            byTest.computeIfAbsent(result.className() + '\n' + result.testName(), key -> new ArrayList<>())
                    .add(result);
        }
        List<List<TestCaseResult>> parts = new ArrayList<>();
        List<TestCaseResult> current = new ArrayList<>();
        long currentBytes = 0;
        for (List<TestCaseResult> test : byTest.values()) {
            long testBytes = test.stream().mapToLong(TestRunIngestedMapper::estimatedJsonBytes).sum();
            if (!current.isEmpty() && currentBytes + testBytes > maxPartBytes) {
                parts.add(current);
                current = new ArrayList<>();
                currentBytes = 0;
            }
            current.addAll(test);
            currentBytes += testBytes;
        }
        parts.add(current);
        return parts;
    }

    /** An upper estimate: JSON escaping (quotes, line breaks) can at most double the length of a message. */
    static long estimatedJsonBytes(TestCaseResult result) {
        return RESULT_OVERHEAD_BYTES + utf8Bytes(result.className()) + utf8Bytes(result.testName())
                + 2L * utf8Bytes(result.failureMessage());
    }

    private static long utf8Bytes(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static TestCaseResult toEvent(io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult result) {
        return new TestCaseResult(
                result.className(),
                result.testName(),
                TestOutcome.valueOf(result.outcome().name()),
                result.duration().toMillis(),
                result.rerunFailures(),
                truncate(result.failureMessage()));
    }

    static String truncate(String message) {
        int limit = TestCaseResult.MAX_FAILURE_MESSAGE_LENGTH;
        if (message == null || message.length() <= limit) {
            return message;
        }
        return message.substring(0, limit - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }
}
