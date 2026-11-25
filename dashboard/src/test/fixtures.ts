import type { DailyTrend, RepositorySummary, TestDetails, TestSummary } from '../api/types';

// Shaped after the demo data: acme/checkout's appliesDiscount becomes flaky over three runs.

export const FLAKY_TEST_ID = 'ce1cbd14-a6b1-4100-8d48-2441ee64eb6f';
export const STABLE_TEST_ID = '99bbab5d-0ef6-4bfb-893a-3931e5cd01da';

export const repositories: RepositorySummary[] = [
  {
    repository: 'acme/checkout',
    tests: 4,
    stable: 3,
    suspicious: 0,
    flaky: 1,
    quarantined: 0,
    lastRunAt: '2026-11-25T14:19:01.019Z',
  },
  {
    repository: 'YagmurPazi/testpulse',
    tests: 7,
    stable: 7,
    suspicious: 0,
    flaky: 0,
    quarantined: 0,
    lastRunAt: '2026-11-24T09:00:00Z',
  },
];

export const checkoutTests: TestSummary[] = [
  {
    testId: FLAKY_TEST_ID,
    className: 'io.example.checkout.CheckoutServiceTest',
    testName: 'appliesDiscount',
    status: 'FLAKY',
    statusSince: '2026-11-25T14:19:01.019Z',
    lastReason: 'Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3',
    executions: 3,
    failures: 1,
  },
  {
    testId: STABLE_TEST_ID,
    className: 'io.example.checkout.CheckoutServiceTest',
    testName: 'calculatesTotal',
    status: 'STABLE',
    statusSince: '2026-11-25T14:19:00.982Z',
    lastReason: null,
    executions: 3,
    failures: 0,
  },
];

export const flakyTestDetails: TestDetails = {
  testId: FLAKY_TEST_ID,
  repository: 'acme/checkout',
  className: 'io.example.checkout.CheckoutServiceTest',
  testName: 'appliesDiscount',
  status: 'FLAKY',
  statusSince: '2026-11-25T14:19:01.019Z',
  executions: [
    {
      runId: 'd705327e-7c10-428b-90dd-13b02b3a0dcf',
      commitSha: '4a1b2c3d4e5f60718293a4b5c6d7e8f901234567',
      attempt: 1,
      outcome: 'PASSED',
      rerunFailures: 1,
      durationMillis: 1731,
      executedAt: '2026-11-25T14:19:01.019Z',
    },
    {
      runId: 'a03f8a72-6fb3-4271-b740-23aec900d12e',
      commitSha: '9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345',
      attempt: 2,
      outcome: 'PASSED',
      rerunFailures: 0,
      durationMillis: 214,
      executedAt: '2026-11-25T14:19:00.999Z',
    },
    {
      runId: 'fea4c1bc-3c25-4cc0-a4d5-73f9938f7439',
      commitSha: '9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345',
      attempt: 1,
      outcome: 'ERROR',
      rerunFailures: 0,
      durationMillis: 1503,
      executedAt: '2026-11-25T14:19:00.982Z',
    },
  ],
  statusChanges: [
    {
      from: 'STABLE',
      to: 'SUSPICIOUS',
      reason: 'Passed and failed on the same commit 9f2c4e1',
      changedAt: '2026-11-25T14:19:00.999Z',
      triggeringRunId: 'a03f8a72-6fb3-4271-b740-23aec900d12e',
    },
    {
      from: 'SUSPICIOUS',
      to: 'FLAKY',
      reason: 'Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3',
      changedAt: '2026-11-25T14:19:01.019Z',
      triggeringRunId: 'd705327e-7c10-428b-90dd-13b02b3a0dcf',
    },
  ],
};

export const checkoutTrend: DailyTrend[] = [
  { day: '2026-11-23', runs: 1, executions: 4, failures: 0, passedOnRerun: 0 },
  { day: '2026-11-25', runs: 3, executions: 12, failures: 1, passedOnRerun: 1 },
];
