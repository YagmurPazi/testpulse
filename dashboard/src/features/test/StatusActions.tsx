import { useState } from 'react';
import { useChangeTestStatus, type StatusAction } from '../../api/queries';
import type { TestStatus } from '../../api/types';
import { ErrorMessage } from '../../components/QueryStates';
import styles from './TestPage.module.css';

const ACTIONS: Partial<Record<TestStatus, { action: StatusAction; label: string; explanation: string }>> = {
  FLAKY: {
    action: 'quarantine',
    label: 'Quarantine',
    explanation:
      'Quarantining records the team’s decision to ignore this test’s result while it is being fixed. ' +
      'Automatic evaluations no longer change its status until it is released.',
  },
  QUARANTINED: {
    action: 'release',
    label: 'Release',
    explanation:
      'Releasing returns the test to suspicious: it is observed again, and becomes flaky only on new evidence.',
  },
};

/**
 * Manual status decisions. The domain allows quarantine only for flaky tests and release only for
 * quarantined ones; the buttons mirror that, and the API's 409 covers a status that changed meanwhile.
 */
export function StatusActions({ testId, status }: { testId: string; status: TestStatus }) {
  const [confirming, setConfirming] = useState(false);
  const mutation = useChangeTestStatus(testId);
  const available = ACTIONS[status];

  if (!available) {
    return (
      <p className="muted">
        Only flaky tests can be quarantined. TestPulse changes this test’s status automatically.
      </p>
    );
  }

  const confirm = () => {
    mutation.mutate(available.action, {
      onSettled: () => {
        setConfirming(false);
      },
    });
  };

  return (
    <div className={styles.actions}>
      {!confirming && (
        <button
          type="button"
          className="button"
          onClick={() => {
            mutation.reset();
            setConfirming(true);
          }}
        >
          {available.label}…
        </button>
      )}
      {confirming && (
        <div role="group" aria-labelledby="confirm-text" className={`card ${styles.confirm}`}>
          <p id="confirm-text">{available.explanation}</p>
          <div className={styles.confirmButtons}>
            <button
              type="button"
              className="button button-primary"
              onClick={confirm}
              disabled={mutation.isPending}
            >
              {mutation.isPending ? 'Saving…' : `${available.label} test`}
            </button>
            <button
              type="button"
              className="button"
              onClick={() => {
                setConfirming(false);
              }}
              disabled={mutation.isPending}
            >
              Cancel
            </button>
          </div>
        </div>
      )}
      {mutation.isError && <ErrorMessage error={mutation.error} />}
    </div>
  );
}
