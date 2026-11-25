package io.github.yagmurpazi.testpulse.analysis.application.port.in;

import java.util.UUID;

/** No monitored test with the given id. */
public class TestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TestNotFoundException(UUID testId) {
        super("No test with id " + testId);
    }
}
