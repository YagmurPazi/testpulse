import { Link, useParams, useSearchParams } from 'react-router';
import { useRepositories, useTests, useTrend } from '../../api/queries';
import { ErrorMessage, Loading } from '../../components/QueryStates';
import { RelativeTime } from '../../components/Time';
import styles from './RepositoryPage.module.css';
import { parseDays, parseStatus, TREND_DAYS } from './searchParams';
import { StatusFilter } from './StatusFilter';
import { TestTable } from './TestTable';
import { fillDays } from './trend';
import { TrendChart } from './TrendChart';

export function RepositoryPage() {
  const { owner = '', name = '' } = useParams();
  const repository = `${owner}/${name}`;
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const days = parseDays(searchParams.get('days'));

  const repositories = useRepositories();
  const tests = useTests(repository, status);
  const trend = useTrend(repository, days);
  const summary = repositories.data?.find((repo) => repo.repository === repository);

  if (repositories.isSuccess && !summary) {
    return (
      <>
        <title>Repository not found · TestPulse</title>
        <h1>Repository not found</h1>
        <p>
          TestPulse has not analysed any run of <code>{repository}</code>.{' '}
          <Link to="/">All repositories</Link>
        </p>
      </>
    );
  }

  const selectDays = (value: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('days', String(value));
    setSearchParams(params, { replace: true });
  };

  return (
    <>
      <title>{`${repository} · TestPulse`}</title>
      <ul className="breadcrumbs">
        <li>
          <Link to="/">Repositories</Link>
        </li>
        <li aria-current="page">{repository}</li>
      </ul>
      <header className="page-header">
        <h1>{repository}</h1>
        {summary?.lastRunAt && (
          <p className="muted">
            Last run <RelativeTime iso={summary.lastRunAt} />
          </p>
        )}
      </header>

      <section className="section" aria-labelledby="trend-heading">
        <div className={styles.sectionHeader}>
          <h2 id="trend-heading">Failures per day</h2>
          <div role="group" aria-label="Time range" className={styles.segmented}>
            {TREND_DAYS.map((value) => (
              <button
                key={value}
                type="button"
                className="button"
                aria-pressed={value === days}
                onClick={() => {
                  selectDays(value);
                }}
              >
                {value} days
              </button>
            ))}
          </div>
        </div>
        {trend.isPending && <Loading label="Loading trend…" />}
        {trend.isError && <ErrorMessage error={trend.error} onRetry={() => void trend.refetch()} />}
        {trend.isSuccess && <TrendChart trend={fillDays(trend.data, days, new Date())} days={days} />}
      </section>

      <section className="section" aria-labelledby="tests-heading">
        <h2 id="tests-heading">Tests</h2>
        <StatusFilter active={status} summary={summary} />
        {tests.isPending && <Loading label="Loading tests…" />}
        {tests.isError && <ErrorMessage error={tests.error} onRetry={() => void tests.refetch()} />}
        {tests.isSuccess && <TestTable tests={tests.data} filter={status} />}
      </section>
    </>
  );
}
