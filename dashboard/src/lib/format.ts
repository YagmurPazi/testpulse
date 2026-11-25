const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
const shortDay = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

/** "25 Nov 2026, 14:19" in the viewer's time zone. */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** "3 hours ago", "yesterday", "just now". */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const seconds = Math.round((new Date(iso).getTime() - now.getTime()) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) {
      return relative.format(Math.trunc(seconds / size), unit);
    }
  }
  return 'just now';
}

/** "25 Nov" for an ISO day (YYYY-MM-DD); days are UTC, like the API's daily buckets. */
export function formatDay(day: string): string {
  return shortDay.format(new Date(`${day}T00:00:00Z`));
}

/** The 7-character abbreviation Git shows. */
export function shortSha(sha: string): string {
  return sha.slice(0, 7);
}

/** "214 ms", "1.7 s", "2 min 5 s". */
export function formatDuration(millis: number): string {
  if (millis < 1000) {
    return `${millis} ms`;
  }
  if (millis < 60_000) {
    return `${(millis / 1000).toFixed(1)} s`;
  }
  const totalSeconds = Math.round(millis / 1000);
  return `${Math.floor(totalSeconds / 60)} min ${totalSeconds % 60} s`;
}

/** "io.example.checkout.CheckoutServiceTest" → "CheckoutServiceTest". */
export function simpleClassName(className: string): string {
  return className.slice(className.lastIndexOf('.') + 1);
}

export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : pluralForm}`;
}
