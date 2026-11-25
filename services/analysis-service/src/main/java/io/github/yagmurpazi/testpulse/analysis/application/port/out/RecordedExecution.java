package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import java.util.Objects;
import java.util.UUID;

/**
 * An execution to store, with data that detection does not use yet.
 *
 * @param testId         the test
 * @param execution      the execution
 * @param durationMillis execution time, kept for trends and future strategies
 */
public record RecordedExecution(UUID testId, TestExecution execution, long durationMillis) {

    public RecordedExecution {
        Objects.requireNonNull(testId, "testId");
        Objects.requireNonNull(execution, "execution");
    }
}
