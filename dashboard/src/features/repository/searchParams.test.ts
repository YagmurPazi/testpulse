import { parseDays, parseStatus } from './searchParams';

describe('parseStatus', () => {
  it('accepts known statuses', () => {
    expect(parseStatus('FLAKY')).toBe('FLAKY');
    expect(parseStatus('QUARANTINED')).toBe('QUARANTINED');
  });

  it.each([null, '', 'flaky', 'BROKEN'])('treats %j as no filter', (value) => {
    expect(parseStatus(value)).toBeNull();
  });
});

describe('parseDays', () => {
  it('accepts the offered ranges', () => {
    expect(parseDays('7')).toBe(7);
    expect(parseDays('90')).toBe(90);
  });

  it.each([null, '', '14', '365', 'abc'])('falls back to 30 days for %j', (value) => {
    expect(parseDays(value)).toBe(30);
  });
});
