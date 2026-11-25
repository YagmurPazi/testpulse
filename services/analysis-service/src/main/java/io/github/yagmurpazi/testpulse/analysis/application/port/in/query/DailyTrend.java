package io.github.yagmurpazi.testpulse.analysis.application.port.in.query;

import java.time.LocalDate;

/** Execution counts of one day (UTC) in a repository. */
public record DailyTrend(LocalDate day, int runs, int executions, int failures, int passedOnRerun) {
}
