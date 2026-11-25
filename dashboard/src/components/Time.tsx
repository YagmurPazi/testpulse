import { formatDateTime, formatRelative } from '../lib/format';

/** A relative time ("3 hours ago") with the exact date and time on hover and for assistive technology. */
export function RelativeTime({ iso }: { iso: string }) {
  return (
    <time dateTime={iso} title={formatDateTime(iso)}>
      {formatRelative(iso)}
    </time>
  );
}
