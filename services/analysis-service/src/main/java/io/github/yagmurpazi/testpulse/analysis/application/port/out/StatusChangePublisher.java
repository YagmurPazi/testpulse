package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;

/** Announces status changes to other services; called inside the same transaction as the change (outbox). */
public interface StatusChangePublisher {

    void publish(StatusChange change);
}
