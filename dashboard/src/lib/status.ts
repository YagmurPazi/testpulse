import type { TestStatus } from '../api/types';

export const STATUS_LABELS: Record<TestStatus, string> = {
  FLAKY: 'Flaky',
  QUARANTINED: 'Quarantined',
  SUSPICIOUS: 'Suspicious',
  STABLE: 'Stable',
};
