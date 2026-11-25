import { ApiError } from '../api/client';
import styles from './QueryStates.module.css';

export function Loading({ label }: { label: string }) {
  return (
    <p className={styles.loading} role="status">
      {label}
    </p>
  );
}

export function ErrorMessage({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  const title =
    error instanceof ApiError && error.problem.title ? error.problem.title : 'Something went wrong';
  return (
    <div className={styles.error} role="alert">
      <p className={styles.errorTitle}>{title}</p>
      <p>{error.message}</p>
      {onRetry && (
        <button type="button" className="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
