package io.github.yagmurpazi.testpulse.analysis.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds test histories in reading order: the first execution added is the oldest. */
final class Histories {

    static final TestKey KEY =
            new TestKey("acme/checkout", "io.example.checkout.CheckoutServiceTest", "appliesDiscount");

    private final List<TestExecution> executions = new ArrayList<>();
    private Instant clock = Instant.parse("2026-11-01T00:00:00Z");

    static Histories history() {
        return new Histories();
    }

    /** One execution per character: P passed, F failed, E error, S skipped, R passed after one rerun. */
    static TestHistory of(String outcomes) {
        Histories history = history();
        for (char outcome : outcomes.toCharArray()) {
            String commit = "c" + history.executions.size() + "aaaaaa";
            switch (outcome) {
                case 'P' -> history.pass(commit);
                case 'F' -> history.fail(commit);
                case 'E' -> history.add(commit, Outcome.ERROR, 0);
                case 'S' -> history.add(commit, Outcome.SKIPPED, 0);
                case 'R' -> history.add(commit, Outcome.PASSED, 1);
                default -> throw new IllegalArgumentException("unknown outcome " + outcome);
            }
        }
        return history.build();
    }

    Histories pass(String commit) {
        return add(commit, Outcome.PASSED, 0);
    }

    Histories fail(String commit) {
        return add(commit, Outcome.FAILED, 0);
    }

    Histories add(String commit, Outcome outcome, int rerunFailures) {
        clock = clock.plusSeconds(60);
        executions.add(new TestExecution(UUID.randomUUID(), commit, 1, outcome, rerunFailures, clock));
        return this;
    }

    /** Event time of the latest execution added. */
    Instant lastTime() {
        return clock;
    }

    TestHistory build() {
        return new TestHistory(KEY, executions);
    }
}
