package io.github.yagmurpazi.testpulse.analysis.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An ingested run to analyse. Built by the messaging adapter from a {@code TestRunIngested} event, so the
 * application does not depend on the event contract.
 *
 * @param eventId    id of the delivering event; processing is idempotent per event
 * @param runId      the run
 * @param repository repository in {@code owner/name} form
 * @param commitSha  commit the tests ran against
 * @param attempt    CI attempt
 * @param runTime    event time of the run; executions and status changes are dated with it
 * @param tests      results of the run
 */
public record ProcessTestRunCommand(
        UUID eventId,
        UUID runId,
        String repository,
        String commitSha,
        int attempt,
        Instant runTime,
        List<ExecutedTest> tests) {

    public ProcessTestRunCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(runTime, "runTime");
        tests = List.copyOf(tests);
    }
}
