import { Link } from 'react-router';
import { useRepositories } from '../../api/queries';
import type { RepositorySummary } from '../../api/types';
import { ErrorMessage, Loading } from '../../components/QueryStates';
import { RelativeTime } from '../../components/Time';
import { byAttention } from './order';
import styles from './RepositoriesPage.module.css';

export function RepositoriesPage() {
  const repositories = useRepositories();

  return (
    <>
      <title>Repositories · TestPulse</title>
      <header className="page-header">
        <div>
          <h1>Repositories</h1>
          <p className="muted">Test health across every analysed repository, most urgent first.</p>
        </div>
      </header>
      {repositories.isPending && <Loading label="Loading repositories…" />}
      {repositories.isError && (
        <ErrorMessage error={repositories.error} onRetry={() => void repositories.refetch()} />
      )}
      {repositories.isSuccess &&
        (repositories.data.length === 0 ? (
          <EmptyState />
        ) : (
          <RepositoryTable repositories={repositories.data.toSorted(byAttention)} />
        ))}
    </>
  );
}

function RepositoryTable({ repositories }: { repositories: RepositorySummary[] }) {
  return (
    <div className="table-scroll">
      <table className="table">
        <caption className="visually-hidden">Repositories and their test counts per status</caption>
        <thead>
          <tr>
            <th scope="col">Repository</th>
            <th scope="col" className="numeric">
              Tests
            </th>
            <th scope="col" className="numeric">
              Flaky
            </th>
            <th scope="col" className="numeric">
              Quarantined
            </th>
            <th scope="col" className="numeric">
              Suspicious
            </th>
            <th scope="col" className="numeric">
              Stable
            </th>
            <th scope="col">Last run</th>
          </tr>
        </thead>
        <tbody>
          {repositories.map((repo) => (
            <tr key={repo.repository}>
              <th scope="row">
                <Link to={`/repositories/${repo.repository}`}>{repo.repository}</Link>
              </th>
              <td className="numeric">{repo.tests}</td>
              <Count value={repo.flaky} highlight={styles.flaky} />
              <Count value={repo.quarantined} highlight={styles.quarantined} />
              <Count value={repo.suspicious} highlight={styles.suspicious} />
              <td className="numeric">{repo.stable}</td>
              <td>{repo.lastRunAt ? <RelativeTime iso={repo.lastRunAt} /> : '–'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Count({ value, highlight }: { value: number; highlight: string | undefined }) {
  return <td className={`numeric ${value > 0 ? (highlight ?? '') : 'muted'}`}>{value}</td>;
}

function EmptyState() {
  return (
    <div className={`card ${styles.empty}`}>
      <h2>No test runs analysed yet</h2>
      <p>Upload JUnit XML reports to ingestion-service, and the analysed tests appear here:</p>
      <pre>
        <code>
          curl -F repository=acme/shop -F commitSha=9f2c4e1 -F branch=main \{'\n'}
          {'     '}-F reports=@TEST-com.acme.CartTest.xml http://localhost:8081/api/v1/test-runs
        </code>
      </pre>
    </div>
  );
}
