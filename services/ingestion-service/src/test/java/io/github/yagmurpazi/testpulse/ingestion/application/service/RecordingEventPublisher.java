package io.github.yagmurpazi.testpulse.ingestion.application.service;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunEventPublisher;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.ArrayList;
import java.util.List;

/** Test double: remembers published runs. */
class RecordingEventPublisher implements TestRunEventPublisher {

    private final List<TestRun> published = new ArrayList<>();

    @Override
    public void publishIngested(TestRun run) {
        published.add(run);
    }

    List<TestRun> published() {
        return List.copyOf(published);
    }
}
