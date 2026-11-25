package io.github.yagmurpazi.testpulse.analysis.application.port.in;

/** Records the results of an ingested run, evaluates the affected tests and applies status changes. */
public interface ProcessTestRunUseCase {

    /** Idempotent per event: a redelivered event changes nothing and reports {@code duplicate}. */
    ProcessingResult process(ProcessTestRunCommand command);
}
