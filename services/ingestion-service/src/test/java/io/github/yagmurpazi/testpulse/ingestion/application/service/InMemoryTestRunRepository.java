package io.github.yagmurpazi.testpulse.ingestion.application.service;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Test double: a map instead of a database. */
class InMemoryTestRunRepository implements TestRunRepository {

    private final Map<RunIdentity, TestRun> runs = new LinkedHashMap<>();

    @Override
    public Optional<TestRun> findByIdentity(RunIdentity identity) {
        return Optional.ofNullable(runs.get(identity));
    }

    @Override
    public boolean saveIfAbsent(TestRun run) {
        return runs.putIfAbsent(run.identity(), run) == null;
    }

    int size() {
        return runs.size();
    }
}
