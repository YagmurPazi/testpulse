package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import io.github.yagmurpazi.testpulse.ingestion.domain.CommitSha;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import java.util.List;
import java.util.Objects;

/** Input of {@link IngestTestRunUseCase}: a parsed CI run, not yet stored. */
public record IngestTestRunCommand(
        RunIdentity identity,
        CommitSha commitSha,
        String branch,
        List<TestCaseResult> results) {

    public IngestTestRunCommand {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        results = List.copyOf(results);
    }
}
