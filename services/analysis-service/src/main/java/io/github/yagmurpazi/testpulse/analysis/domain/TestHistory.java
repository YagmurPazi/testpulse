package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Recent executions of one test, oldest first. Usually a window of the latest executions, not the full history.
 *
 * @param key        the test
 * @param executions executions ordered by {@link TestExecution#executedAt()}, oldest first
 */
public record TestHistory(TestKey key, List<TestExecution> executions) {

    public TestHistory {
        Objects.requireNonNull(key, "key");
        executions = List.copyOf(
                executions.stream().sorted(Comparator.comparing(TestExecution::executedAt)).toList());
    }

    /** Executions in which the test actually ran (not skipped), oldest first. */
    public List<TestExecution> ran() {
        return executions.stream().filter(execution -> execution.outcome().ran()).toList();
    }

    /** Number of clean passes at the end of the history, ignoring skipped executions. */
    public int trailingCleanPasses() {
        List<TestExecution> ran = ran();
        int count = 0;
        for (int i = ran.size() - 1; i >= 0 && ran.get(i).cleanPass(); i--) {
            count++;
        }
        return count;
    }
}
