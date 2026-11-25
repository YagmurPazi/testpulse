import type { Execution } from '../../api/types';
import { formatDateTime, shortSha } from '../../lib/format';
import { describeOutcome } from '../../lib/status';
import styles from './TestPage.module.css';

/**
 * The latest executions as a row of squares, oldest on the left: a flaky test shows up as a mix of colors
 * on the same commit, a broken one as a solid block of red.
 */
export function ResultStrip({ executions }: { executions: Execution[] }) {
  const oldestFirst = executions.toReversed();
  return (
    <ol className={styles.strip} aria-label="Latest results, oldest first">
      {oldestFirst.map((execution) => {
        const label = `${describeOutcome(execution.outcome, execution.rerunFailures)} on ${shortSha(
          execution.commitSha,
        )}, attempt ${execution.attempt}, ${formatDateTime(execution.executedAt)}`;
        const kind = execution.rerunFailures > 0 ? 'rerun' : execution.outcome.toLowerCase();
        return (
          <li key={execution.runId} className={`${styles.result} ${styles[kind] ?? ''}`} title={label}>
            <span className="visually-hidden">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}
