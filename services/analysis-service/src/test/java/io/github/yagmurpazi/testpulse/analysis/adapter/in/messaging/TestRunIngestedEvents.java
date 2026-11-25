package io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging;

import io.github.yagmurpazi.testpulse.events.TestCaseResult;
import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sample events as ingestion-service publishes them. */
final class TestRunIngestedEvents {

    private TestRunIngestedEvents() {
    }

    static TestRunIngested event(String repository, String... testNames) {
        return new TestRunIngested(UUID.randomUUID(), TestRunIngested.SCHEMA_VERSION,
                Instant.parse("2026-11-25T10:00:00Z"), UUID.randomUUID(), repository, "9f2c4e1a7b", "main", "1001",
                1, List.of(testNames).stream()
                        .map(name -> new TestCaseResult("io.example.CheckoutTest", name, TestOutcome.PASSED, 12, 0,
                                null))
                        .toList());
    }
}
