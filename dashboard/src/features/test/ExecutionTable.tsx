import type { Execution } from '../../api/types';
import { formatDateTime, formatDuration, shortSha } from '../../lib/format';
import { describeOutcome } from '../../lib/status';
import styles from './TestPage.module.css';

export function ExecutionTable({ executions }: { executions: Execution[] }) {
  return (
    <div className="table-scroll">
      <table className="table">
        <caption className="visually-hidden">Latest executions, newest first</caption>
        <thead>
          <tr>
            <th scope="col">When</th>
            <th scope="col">Commit</th>
            <th scope="col" className="numeric">
              Attempt
            </th>
            <th scope="col">Outcome</th>
            <th scope="col" className="numeric">
              Duration
            </th>
          </tr>
        </thead>
        <tbody>
          {executions.map((execution) => (
            <tr key={execution.runId}>
              <td>
                <time dateTime={execution.executedAt}>{formatDateTime(execution.executedAt)}</time>
              </td>
              <td>
                <code title={execution.commitSha}>{shortSha(execution.commitSha)}</code>
              </td>
              <td className="numeric">{execution.attempt}</td>
              <td className={styles[`outcome${execution.rerunFailures > 0 ? 'Rerun' : execution.outcome}`]}>
                {describeOutcome(execution.outcome, execution.rerunFailures)}
              </td>
              <td className="numeric">{formatDuration(execution.durationMillis)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
