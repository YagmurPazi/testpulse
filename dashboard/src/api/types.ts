// Mirrors the JSON of analysis-service's query API (TestQueryController, TestStatusController).
// Instants are ISO-8601 strings in UTC, days are ISO dates (YYYY-MM-DD).

/** Statuses ordered from most to least severe, the order the API sorts tests in. */
export const TEST_STATUSES = ['FLAKY', 'QUARANTINED', 'SUSPICIOUS', 'STABLE'] as const;
export type TestStatus = (typeof TEST_STATUSES)[number];

export type Outcome = 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';

export interface RepositorySummary {
  repository: string;
  tests: number;
  stable: number;
  suspicious: number;
  flaky: number;
  quarantined: number;
  lastRunAt: string | null;
}

export interface TestSummary {
  testId: string;
  className: string;
  testName: string;
  status: TestStatus;
  statusSince: string;
  /** Reason of the latest status change; null while the test has never changed status. */
  lastReason: string | null;
  executions: number;
  failures: number;
}

export interface Execution {
  runId: string;
  commitSha: string;
  attempt: number;
  outcome: Outcome;
  /** Failed attempts before the final outcome within one run (Surefire reruns). */
  rerunFailures: number;
  durationMillis: number;
  executedAt: string;
}

export interface StatusChange {
  from: TestStatus;
  to: TestStatus;
  reason: string;
  changedAt: string;
  /** The run whose evaluation caused the change; null for manual changes. */
  triggeringRunId: string | null;
}

export interface TestDetails {
  testId: string;
  repository: string;
  className: string;
  testName: string;
  status: TestStatus;
  statusSince: string;
  /** Newest first. */
  executions: Execution[];
  /** Oldest first. */
  statusChanges: StatusChange[];
}

export interface DailyTrend {
  day: string;
  runs: number;
  executions: number;
  failures: number;
  passedOnRerun: number;
}

export interface StatusChangeResult {
  testId: string;
  from: TestStatus;
  to: TestStatus;
  reason: string;
  changedAt: string;
}

/** RFC 9457 problem details, as returned for every API error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  /** Set on 409 Conflict for a status change the test's current status does not allow. */
  currentStatus?: TestStatus;
}
