import { Bar, BarChart, CartesianGrid, Legend, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyTrend } from '../../api/types';
import { formatDay, plural } from '../../lib/format';
import styles from './RepositoryPage.module.css';
import { totals } from './trend';

/**
 * Failed executions and executions that passed only after a rerun, per day. The second series is the one
 * that matters for flakiness: those tests passed, so the build was green, but they did fail first.
 */
export function TrendChart({ trend, days }: { trend: DailyTrend[]; days: number }) {
  const sum = totals(trend);
  return (
    <figure className={`card ${styles.trend}`}>
      <figcaption>
        Last {days} days: {plural(sum.executions, 'execution')} in {plural(sum.runs, 'run')},{' '}
        <strong>{sum.failures} failed</strong>, <strong>{sum.passedOnRerun} passed only after a rerun</strong>
        .
      </figcaption>
      {/* Hidden from assistive technology, without Recharts' keyboard layer that would make it focusable:
          the caption and the table carry the same data. */}
      <BarChart
        responsive
        accessibilityLayer={false}
        data={trend}
        style={{ width: '100%', height: 240 }}
        aria-hidden="true"
      >
        <CartesianGrid vertical={false} stroke="var(--border)" />
        <XAxis dataKey="day" tickFormatter={formatDay} stroke="var(--text-muted)" minTickGap={16} />
        <YAxis allowDecimals={false} stroke="var(--text-muted)" width={32} />
        <Tooltip
          labelFormatter={(day: unknown) => (typeof day === 'string' ? formatDay(day) : '')}
          contentStyle={{ background: 'var(--surface)', borderColor: 'var(--border)' }}
          cursor={{ fill: 'var(--bg)' }}
        />
        <Legend />
        <Bar dataKey="failures" name="Failed" stackId="day" fill="var(--failed)" />
        <Bar dataKey="passedOnRerun" name="Passed after rerun" stackId="day" fill="var(--suspicious)" />
      </BarChart>
      <details className={styles.trendTable}>
        <summary>Show as table</summary>
        <table className="table">
          <caption className="visually-hidden">Executions per day</caption>
          <thead>
            <tr>
              <th scope="col">Day</th>
              <th scope="col" className="numeric">
                Runs
              </th>
              <th scope="col" className="numeric">
                Executions
              </th>
              <th scope="col" className="numeric">
                Failed
              </th>
              <th scope="col" className="numeric">
                Passed after rerun
              </th>
            </tr>
          </thead>
          <tbody>
            {trend
              .filter((point) => point.runs > 0)
              .map((point) => (
                <tr key={point.day}>
                  <th scope="row">{formatDay(point.day)}</th>
                  <td className="numeric">{point.runs}</td>
                  <td className="numeric">{point.executions}</td>
                  <td className="numeric">{point.failures}</td>
                  <td className="numeric">{point.passedOnRerun}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
