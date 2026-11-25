package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage of monitored tests. The {@code find} methods lock the returned tests until the transaction ends, so a
 * manual quarantine and the processing of a run cannot overwrite each other's status change.
 */
public interface MonitoredTestRepository {

    Map<TestKey, MonitoredTest> findByKeysForUpdate(Collection<TestKey> keys);

    Optional<MonitoredTest> findByIdForUpdate(UUID id);

    void insert(Collection<MonitoredTest> tests);

    void updateStatus(MonitoredTest test);
}
