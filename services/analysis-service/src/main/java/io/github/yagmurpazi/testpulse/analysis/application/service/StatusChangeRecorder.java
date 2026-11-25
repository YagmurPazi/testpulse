package io.github.yagmurpazi.testpulse.analysis.application.service;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangePublisher;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.util.Objects;

/** Every status change, automatic or manual, is both kept in the log and published. */
public final class StatusChangeRecorder {

    private final StatusChangeLog log;
    private final StatusChangePublisher publisher;

    public StatusChangeRecorder(StatusChangeLog log, StatusChangePublisher publisher) {
        this.log = Objects.requireNonNull(log, "log");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    void recordAndPublish(StatusChange change) {
        log.record(change);
        publisher.publish(change);
    }
}
