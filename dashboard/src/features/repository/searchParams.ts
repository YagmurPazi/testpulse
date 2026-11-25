import { TEST_STATUSES, type TestStatus } from '../../api/types';

export const TREND_DAYS = [7, 30, 90] as const;
export type TrendDays = (typeof TREND_DAYS)[number];
export const DEFAULT_TREND_DAYS: TrendDays = 30;

/** The status filter from the URL; anything unknown means "all statuses" rather than an error. */
export function parseStatus(value: string | null): TestStatus | null {
  return TEST_STATUSES.find((status) => status === value) ?? null;
}

export function parseDays(value: string | null): TrendDays {
  return TREND_DAYS.find((days) => String(days) === value) ?? DEFAULT_TREND_DAYS;
}
