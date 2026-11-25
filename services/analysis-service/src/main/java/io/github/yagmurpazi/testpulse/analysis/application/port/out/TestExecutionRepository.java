package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only history of test executions. */
public interface TestExecutionRepository {

    void appendAll(Collection<RecordedExecution> executions);

    /** The latest {@code perTest} executions of each test, oldest first; tests without executions are absent. */
    Map<UUID, List<TestExecution>> findLatest(Collection<UUID> testIds, int perTest);
}
