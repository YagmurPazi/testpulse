package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.Optional;

/** Storage of test runs. */
public interface TestRunRepository {

    Optional<TestRun> findByIdentity(RunIdentity identity);

    /**
     * Stores the run if no run with the same identity exists.
     *
     * <p>Must be atomic with respect to concurrent calls: of two concurrent calls with the same identity,
     * exactly one returns {@code true}.
     *
     * @return {@code true} if the run was stored, {@code false} if its identity already existed
     */
    boolean saveIfAbsent(TestRun run);
}
