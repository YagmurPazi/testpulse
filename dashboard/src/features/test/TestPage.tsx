import { Link, useParams } from 'react-router';
import { ApiError } from '../../api/client';
import { useTestDetails } from '../../api/queries';
import type { TestDetails } from '../../api/types';
import { ErrorMessage, Loading } from '../../components/QueryStates';
import { StatusBadge } from '../../components/StatusBadge';
import { RelativeTime } from '../../components/Time';
import { simpleClassName } from '../../lib/format';
import { ExecutionTable } from './ExecutionTable';
import { ResultStrip } from './ResultStrip';
import { StatusActions } from './StatusActions';
import { StatusHistory } from './StatusHistory';
import styles from './TestPage.module.css';

export function TestPage() {
  const { testId = '' } = useParams();
  const test = useTestDetails(testId);

  if (test.isPending) {
    return <Loading label="Loading test…" />;
  }
  if (test.isError) {
    if (test.error instanceof ApiError && test.error.status === 404) {
      return (
        <>
          <title>Test not found · TestPulse</title>
          <h1>Test not found</h1>
          <p>
            No test with this id has been analysed. <Link to="/">All repositories</Link>
          </p>
        </>
      );
    }
    return <ErrorMessage error={test.error} onRetry={() => void test.refetch()} />;
  }
  return <TestView test={test.data} />;
}

function TestView({ test }: { test: TestDetails }) {
  return (
    <>
      <title>{`${test.testName} · ${test.repository} · TestPulse`}</title>
      <ul className="breadcrumbs">
        <li>
          <Link to="/">Repositories</Link>
        </li>
        <li>
          <Link to={`/repositories/${test.repository}`}>{test.repository}</Link>
        </li>
        <li aria-current="page">{test.testName}</li>
      </ul>
      <header className={styles.header}>
        <div>
          <h1>{test.testName}</h1>
          <p className={styles.className}>
            <span title={test.className}>{simpleClassName(test.className)}</span>
            <code className="muted">{test.className}</code>
          </p>
        </div>
        <p className={styles.status}>
          <StatusBadge status={test.status} />
          <span className="muted">
            since <RelativeTime iso={test.statusSince} />
          </span>
        </p>
      </header>

      <StatusActions key={test.status} testId={test.testId} status={test.status} />

      <section className="section" aria-labelledby="results-heading">
        <h2 id="results-heading">Latest results</h2>
        {test.executions.length > 0 ? (
          <ResultStrip executions={test.executions} />
        ) : (
          <p className="muted">No executions recorded.</p>
        )}
      </section>

      <section className="section" aria-labelledby="history-heading">
        <h2 id="history-heading">Status history</h2>
        <StatusHistory changes={test.statusChanges} />
      </section>

      <section className="section" aria-labelledby="executions-heading">
        <h2 id="executions-heading">Executions</h2>
        <ExecutionTable executions={test.executions} />
      </section>
    </>
  );
}
