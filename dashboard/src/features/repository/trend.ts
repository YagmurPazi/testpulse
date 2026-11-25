import type { DailyTrend } from '../../api/types';

const DAY_MS = 24 * 3600 * 1000;

function isoDay(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

/**
 * One entry per day for the last {@link days} days up to today (UTC), with zeros for days without runs:
 * the API only returns days that had executions, but a chart with gaps hides how quiet a repository was.
 */
export function fillDays(trend: DailyTrend[], days: number, now: Date): DailyTrend[] {
  const byDay = new Map(trend.map((point) => [point.day, point]));
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const filled: DailyTrend[] = [];
  for (let offset = days - 1; offset >= 0; offset--) {
    const day = isoDay(today - offset * DAY_MS);
    filled.push(byDay.get(day) ?? { day, runs: 0, executions: 0, failures: 0, passedOnRerun: 0 });
  }
  return filled;
}

export interface TrendTotals {
  runs: number;
  executions: number;
  failures: number;
  passedOnRerun: number;
}

export function totals(trend: DailyTrend[]): TrendTotals {
  return trend.reduce<TrendTotals>(
    (sum, point) => ({
      runs: sum.runs + point.runs,
      executions: sum.executions + point.executions,
      failures: sum.failures + point.failures,
      passedOnRerun: sum.passedOnRerun + point.passedOnRerun,
    }),
    { runs: 0, executions: 0, failures: 0, passedOnRerun: 0 },
  );
}
