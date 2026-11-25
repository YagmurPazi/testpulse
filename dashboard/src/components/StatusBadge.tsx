import type { TestStatus } from '../api/types';
import { STATUS_LABELS } from '../lib/status';
import styles from './StatusBadge.module.css';

export function StatusBadge({ status }: { status: TestStatus }) {
  return (
    <span className={`${styles.badge} ${styles[status.toLowerCase()] ?? ''}`}>{STATUS_LABELS[status]}</span>
  );
}
