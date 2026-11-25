import {
  formatDateTime,
  formatDay,
  formatDuration,
  formatRelative,
  plural,
  shortSha,
  simpleClassName,
} from './format';

describe('formatRelative', () => {
  const now = new Date('2026-11-25T12:00:00Z');

  it.each([
    ['2026-11-25T11:59:30Z', 'just now'],
    ['2026-11-25T11:55:00Z', '5 minutes ago'],
    ['2026-11-25T09:00:00Z', '3 hours ago'],
    ['2026-11-24T12:00:00Z', 'yesterday'],
    ['2026-11-20T12:00:00Z', '5 days ago'],
    ['2026-09-20T12:00:00Z', '2 months ago'],
    ['2024-11-25T12:00:00Z', '2 years ago'],
  ])('formats %s as "%s"', (iso, expected) => {
    expect(formatRelative(iso, now)).toBe(expected);
  });
});

describe('formatDuration', () => {
  it.each([
    [0, '0 ms'],
    [214, '214 ms'],
    [999, '999 ms'],
    [1000, '1.0 s'],
    [1731, '1.7 s'],
    [59_949, '59.9 s'],
    [125_000, '2 min 5 s'],
  ])('formats %i ms as "%s"', (millis, expected) => {
    expect(formatDuration(millis)).toBe(expected);
  });
});

describe('formatting helpers', () => {
  it('formats instants in the local time zone (UTC in tests)', () => {
    expect(formatDateTime('2026-11-25T14:19:01Z')).toBe('25 Nov 2026, 14:19');
  });

  it('formats an ISO day without shifting it by the time zone', () => {
    expect(formatDay('2026-11-25')).toBe('25 Nov');
  });

  it('abbreviates a commit SHA to seven characters', () => {
    expect(shortSha('4a1b2c3d4e5f60718293a4b5c6d7e8f901234567')).toBe('4a1b2c3');
  });

  it('strips the package from a class name', () => {
    expect(simpleClassName('io.example.checkout.CheckoutServiceTest')).toBe('CheckoutServiceTest');
    expect(simpleClassName('DefaultPackageTest')).toBe('DefaultPackageTest');
  });

  it('pluralises', () => {
    expect(plural(1, 'run')).toBe('1 run');
    expect(plural(3, 'run')).toBe('3 runs');
    expect(plural(0, 'test')).toBe('0 tests');
  });
});
