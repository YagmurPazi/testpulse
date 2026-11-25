package io.github.yagmurpazi.testpulse.analysis.domain;

/** A manual action that the current status does not allow, such as quarantining a stable test. */
public class InvalidStatusTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TestStatus currentStatus;
    private final String action;

    public InvalidStatusTransitionException(TestStatus currentStatus, String action) {
        super("Cannot " + action + " a test that is " + currentStatus);
        this.currentStatus = currentStatus;
        this.action = action;
    }

    public TestStatus currentStatus() {
        return currentStatus;
    }

    public String action() {
        return action;
    }
}
