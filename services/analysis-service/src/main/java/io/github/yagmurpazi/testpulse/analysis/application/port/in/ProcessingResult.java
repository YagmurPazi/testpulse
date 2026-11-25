package io.github.yagmurpazi.testpulse.analysis.application.port.in;

import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.util.List;

/**
 * Outcome of processing one run.
 *
 * @param duplicate      the event had been processed before; nothing was changed
 * @param testsEvaluated number of distinct tests evaluated
 * @param changes        status changes caused by the run
 */
public record ProcessingResult(boolean duplicate, int testsEvaluated, List<StatusChange> changes) {

    public ProcessingResult {
        changes = List.copyOf(changes);
    }

    public static ProcessingResult alreadyProcessed() {
        return new ProcessingResult(true, 0, List.of());
    }
}
