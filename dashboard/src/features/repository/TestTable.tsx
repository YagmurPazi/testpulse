import { Link } from 'react-router';
import type { TestStatus, TestSummary } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { STATUS_LABELS } from '../../lib/status';
import { RelativeTime } from '../../components/Time';
import { simpleClassName } from '../../lib/format';
import styles from './RepositoryPage.module.css';

export function TestTable({ tests, filter }: { tests: TestSummary[]; filter: TestStatus | null }) {
  if (tests.length === 0) {
    return (
      <p className="muted">
        {filter ? `No ${STATUS_LABELS[filter].toLowerCase()} tests.` : 'No tests analysed yet.'}
      </p>
    );
  }
  return (
    <div className="table-scroll">
      <table className="table">
        <caption className="visually-hidden">Tests, most severe status first</caption>
        <thead>
          <tr>
            <th scope="col">Test</th>
            <th scope="col">Status</th>
            <th scope="col">Since</th>
            <th scope="col">Latest reason</th>
            <th scope="col" className="numeric">
              Runs
            </th>
            <th scope="col" className="numeric">
              Failures
            </th>
          </tr>
        </thead>
        <tbody>
          {tests.map((test) => (
            <tr key={test.testId}>
              <th scope="row" className={styles.testCell}>
                <Link to={`/tests/${test.testId}`}>{test.testName}</Link>
                <span className={styles.className} title={test.className}>
                  {simpleClassName(test.className)}
                </span>
              </th>
              <td>
                <StatusBadge status={test.status} />
              </td>
              <td>
                <RelativeTime iso={test.statusSince} />
              </td>
              <td className={styles.reason}>{test.lastReason ?? <span className="muted">–</span>}</td>
              <td className="numeric">{test.executions}</td>
              <td className={`numeric ${test.failures > 0 ? styles.failures : 'muted'}`}>{test.failures}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
