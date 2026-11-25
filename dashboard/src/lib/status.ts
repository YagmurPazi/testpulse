import type { Outcome, TestStatus } from '../api/types';

export const STATUS_LABELS: Record<TestStatus, string> = {
  FLAKY: 'Flaky',
  QUARANTINED: 'Quarantined',
  SUSPICIOUS: 'Suspicious',
  STABLE: 'Stable',
};

export const OUTCOME_LABELS: Record<Outcome, string> = {
  PASSED: 'Passed',
  FAILED: 'Failed',
  ERROR: 'Error',
  SKIPPED: 'Skipped',
};

/** "Passed after 2 failed attempts" for a test that only passed on a Surefire rerun. */
export function describeOutcome(outcome: Outcome, rerunFailures: number): string {
  if (rerunFailures === 0) {
    return OUTCOME_LABELS[outcome];
  }
  const attempts = rerunFailures === 1 ? '1 failed attempt' : `${rerunFailures} failed attempts`;
  return `${OUTCOME_LABELS[outcome]} after ${attempts}`;
}
