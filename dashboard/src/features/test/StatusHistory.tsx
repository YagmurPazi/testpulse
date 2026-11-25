import type { StatusChange } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDateTime } from '../../lib/format';
import styles from './TestPage.module.css';

export function StatusHistory({ changes }: { changes: StatusChange[] }) {
  if (changes.length === 0) {
    return <p className="muted">No status changes yet: the test has been stable since it was first seen.</p>;
  }
  return (
    <ol className={styles.history} aria-label="Status changes, newest first">
      {changes.toReversed().map((change) => (
        <li key={`${change.changedAt}-${change.to}`} className={styles.historyItem}>
          <div className={styles.historyTransition}>
            <StatusBadge status={change.from} />
            <span aria-hidden="true">→</span>
            <span className="visually-hidden">to</span>
            <StatusBadge status={change.to} />
            <span className="muted">{change.triggeringRunId ? 'automatic' : 'manual'}</span>
          </div>
          <p className={styles.historyReason}>{change.reason}</p>
          <time className="muted" dateTime={change.changedAt}>
            {formatDateTime(change.changedAt)}
          </time>
        </li>
      ))}
    </ol>
  );
}
