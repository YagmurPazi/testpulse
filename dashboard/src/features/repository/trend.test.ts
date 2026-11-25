import type { DailyTrend } from '../../api/types';
import { fillDays, totals } from './trend';

const point = (day: string, failures: number): DailyTrend => ({
  day,
  runs: 1,
  executions: 4,
  failures,
  passedOnRerun: 0,
});

describe('fillDays', () => {
  const now = new Date('2026-11-25T23:30:00Z');

  it('returns exactly the requested days, ending today, with zeros where nothing ran', () => {
    const filled = fillDays([point('2026-11-23', 2), point('2026-11-25', 1)], 4, now);

    expect(filled.map((p) => [p.day, p.failures])).toEqual([
      ['2026-11-22', 0],
      ['2026-11-23', 2],
      ['2026-11-24', 0],
      ['2026-11-25', 1],
    ]);
    expect(filled[0]).toEqual({ day: '2026-11-22', runs: 0, executions: 0, failures: 0, passedOnRerun: 0 });
  });

  it('drops days the API returned before the window', () => {
    expect(fillDays([point('2026-11-01', 5)], 3, now).map((p) => p.day)).toEqual([
      '2026-11-23',
      '2026-11-24',
      '2026-11-25',
    ]);
  });

  it('crosses month boundaries in UTC', () => {
    const days = fillDays([], 3, new Date('2026-12-01T00:10:00Z')).map((p) => p.day);

    expect(days).toEqual(['2026-11-29', '2026-11-30', '2026-12-01']);
  });
});

describe('totals', () => {
  it('sums every counter', () => {
    expect(totals([point('2026-11-24', 1), { ...point('2026-11-25', 2), passedOnRerun: 1 }])).toEqual({
      runs: 2,
      executions: 8,
      failures: 3,
      passedOnRerun: 1,
    });
  });

  it('is zero for no days', () => {
    expect(totals([])).toEqual({ runs: 0, executions: 0, failures: 0, passedOnRerun: 0 });
  });
});
