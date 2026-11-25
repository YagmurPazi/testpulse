package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;

/** Keeps every status change for the history shown on the dashboard. */
public interface StatusChangeLog {

    void record(StatusChange change);
}
