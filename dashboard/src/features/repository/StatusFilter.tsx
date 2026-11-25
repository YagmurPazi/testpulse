import { Link, useSearchParams } from 'react-router';
import { TEST_STATUSES, type RepositorySummary, type TestStatus } from '../../api/types';
import { STATUS_LABELS } from '../../lib/status';
import styles from './RepositoryPage.module.css';

const COUNT: Record<TestStatus, (summary: RepositorySummary) => number> = {
  FLAKY: (s) => s.flaky,
  QUARANTINED: (s) => s.quarantined,
  SUSPICIOUS: (s) => s.suspicious,
  STABLE: (s) => s.stable,
};

interface Props {
  active: TestStatus | null;
  summary: RepositorySummary | undefined;
}

/** Links rather than buttons: each filter is a URL that can be bookmarked and shared. */
export function StatusFilter({ active, summary }: Props) {
  const [searchParams] = useSearchParams();

  const href = (status: TestStatus | null) => {
    const params = new URLSearchParams(searchParams);
    if (status) {
      params.set('status', status);
    } else {
      params.delete('status');
    }
    const query = params.toString();
    return query ? `?${query}` : '?';
  };

  const options: [TestStatus | null, string, number | undefined][] = [
    [null, 'All', summary?.tests],
    ...TEST_STATUSES.map((status): [TestStatus, string, number | undefined] => [
      status,
      STATUS_LABELS[status],
      summary && COUNT[status](summary),
    ]),
  ];

  return (
    <nav aria-label="Filter tests by status">
      <ul className={styles.filter}>
        {options.map(([status, label, count]) => (
          <li key={label}>
            <Link
              to={href(status)}
              className={styles.filterOption}
              aria-current={status === active ? 'page' : undefined}
              replace
            >
              {label}
              {count !== undefined && <span className={styles.filterCount}>{count}</span>}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
