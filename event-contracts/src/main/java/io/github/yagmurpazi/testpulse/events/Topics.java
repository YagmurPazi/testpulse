package io.github.yagmurpazi.testpulse.events;

/** Kafka topic names. Part of the contract: producers and consumers must agree on them. */
public final class Topics {

    /** {@link TestRunIngested} events, keyed by repository. */
    public static final String TEST_RUNS = "testpulse.test-runs";

    /** {@link TestStatusChanged} events, keyed by repository. */
    public static final String TEST_STATUS_CHANGES = "testpulse.test-status-changes";

    private Topics() {
    }
}
