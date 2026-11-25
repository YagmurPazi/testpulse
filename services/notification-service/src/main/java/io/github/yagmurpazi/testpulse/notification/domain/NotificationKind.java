package io.github.yagmurpazi.testpulse.notification.domain;

/** The status changes worth telling the team about. */
public enum NotificationKind {
    /** Evidence shows the test passes and fails without a code change. */
    BECAME_FLAKY,
    /** The team decided to ignore the test's result while it is being fixed. */
    QUARANTINED,
    /** The team ended a quarantine; the test is observed again. */
    RELEASED,
    /** A flaky test has passed consistently and is stable again. */
    RECOVERED
}
