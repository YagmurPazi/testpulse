import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw/http';
import type { TestDetails, TestStatus } from '../../api/types';
import { FLAKY_TEST_ID, flakyTestDetails } from '../../test/fixtures';
import { renderApp } from '../../test/render';
import { problem, server } from '../../test/server';

const DETAILS_URL = `/api/v1/tests/${FLAKY_TEST_ID}`;

/** A test whose status the fake API keeps, so a status change is visible after the refetch. */
function statefulTest(initial: TestStatus) {
  let current: TestDetails = { ...flakyTestDetails, status: initial };
  const posted: string[] = [];
  const change = (action: string, from: TestStatus, to: TestStatus, reason: string) =>
    http.post(`${DETAILS_URL}/${action}`, () => {
      posted.push(action);
      const changedAt = '2026-11-25T15:00:00Z';
      current = {
        ...current,
        status: to,
        statusSince: changedAt,
        statusChanges: [...current.statusChanges, { from, to, reason, changedAt, triggeringRunId: null }],
      };
      return HttpResponse.json({ testId: FLAKY_TEST_ID, from, to, reason, changedAt });
    });
  server.use(
    http.get(DETAILS_URL, () => HttpResponse.json(current)),
    change('quarantine', 'FLAKY', 'QUARANTINED', 'Quarantined manually'),
    change('release', 'QUARANTINED', 'SUSPICIOUS', 'Released from quarantine'),
  );
  return posted;
}

describe('test page', () => {
  it('shows the test, its status and where it belongs', async () => {
    renderApp(`/tests/${FLAKY_TEST_ID}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'appliesDiscount' })).toBeInTheDocument();
    expect(screen.getByText('io.example.checkout.CheckoutServiceTest')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'acme/checkout' })).toHaveAttribute(
      'href',
      '/repositories/acme/checkout',
    );
  });

  it('shows the latest results oldest first, marking passes after failed attempts', async () => {
    renderApp(`/tests/${FLAKY_TEST_ID}`);

    const strip = await screen.findByRole('list', { name: 'Latest results, oldest first' });
    expect(
      within(strip)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      expect.stringMatching(/^Error on 9f2c4e1, attempt 1/),
      expect.stringMatching(/^Passed on 9f2c4e1, attempt 2/),
      expect.stringMatching(/^Passed after 1 failed attempt on 4a1b2c3, attempt 1/),
    ]);
  });

  it('explains every status change, newest first', async () => {
    renderApp(`/tests/${FLAKY_TEST_ID}`);

    const history = await screen.findByRole('list', { name: 'Status changes, newest first' });
    const [latest, first] = within(history).getAllByRole('listitem');
    expect(latest).toHaveTextContent('Suspicious→Flaky');
    expect(latest).toHaveTextContent(
      'Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3',
    );
    expect(first).toHaveTextContent('Passed and failed on the same commit 9f2c4e1');
  });

  it('lists the executions with commit, attempt and outcome', async () => {
    renderApp(`/tests/${FLAKY_TEST_ID}`);

    const table = await screen.findByRole('table', { name: 'Latest executions, newest first' });
    const newest = within(table).getAllByRole('row')[1];
    expect(newest).toHaveTextContent('4a1b2c3');
    expect(newest).toHaveTextContent('Passed after 1 failed attempt');
    expect(newest).toHaveTextContent('1.7 s');
  });

  it('quarantines a flaky test after confirmation', async () => {
    const posted = statefulTest('FLAKY');
    const { user } = renderApp(`/tests/${FLAKY_TEST_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Quarantine…' }));
    expect(screen.getByRole('group')).toHaveTextContent(/ignore this test’s result/);
    await user.click(screen.getByRole('button', { name: 'Quarantine test' }));

    expect(await screen.findByRole('button', { name: 'Release…' })).toBeInTheDocument();
    expect(screen.getAllByText('Quarantined').length).toBeGreaterThan(0);
    expect(screen.getByText('Quarantined manually')).toBeInTheDocument();
    expect(posted).toEqual(['quarantine']);
  });

  it('does nothing when the confirmation is cancelled', async () => {
    const posted = statefulTest('FLAKY');
    const { user } = renderApp(`/tests/${FLAKY_TEST_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Quarantine…' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByRole('button', { name: 'Quarantine…' })).toBeInTheDocument();
    expect(posted).toEqual([]);
  });

  it('releases a quarantined test back to suspicious', async () => {
    const posted = statefulTest('QUARANTINED');
    const { user } = renderApp(`/tests/${FLAKY_TEST_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Release…' }));
    await user.click(screen.getByRole('button', { name: 'Release test' }));

    expect(await screen.findByText('Released from quarantine')).toBeInTheDocument();
    expect(screen.getByText(/Only flaky tests can be quarantined/)).toBeInTheDocument();
    expect(posted).toEqual(['release']);
  });

  it('offers no manual action for a stable test', async () => {
    server.use(http.get(DETAILS_URL, () => HttpResponse.json({ ...flakyTestDetails, status: 'STABLE' })));

    renderApp(`/tests/${FLAKY_TEST_ID}`);

    expect(await screen.findByText(/Only flaky tests can be quarantined/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Quarantine/ })).not.toBeInTheDocument();
  });

  it('shows a conflict and the current status when the test changed meanwhile', async () => {
    let status: TestStatus = 'FLAKY';
    server.use(
      http.get(DETAILS_URL, () => HttpResponse.json({ ...flakyTestDetails, status })),
      http.post(`${DETAILS_URL}/quarantine`, () => {
        status = 'QUARANTINED';
        return problem(409, 'Status change not allowed', 'Cannot quarantine a test that is QUARANTINED', {
          currentStatus: 'QUARANTINED',
        });
      }),
    );
    const { user } = renderApp(`/tests/${FLAKY_TEST_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Quarantine…' }));
    await user.click(screen.getByRole('button', { name: 'Quarantine test' }));

    // The refetched status replaces the stale button; the remounted actions start without the error.
    expect(await screen.findByRole('button', { name: 'Release…' })).toBeInTheDocument();
  });

  it('keeps the error visible when a status change fails without a status change', async () => {
    server.use(
      http.post(`${DETAILS_URL}/quarantine`, () => problem(503, 'Service Unavailable', 'Database is down')),
    );
    const { user } = renderApp(`/tests/${FLAKY_TEST_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Quarantine…' }));
    await user.click(screen.getByRole('button', { name: 'Quarantine test' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Database is down');
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Quarantine…' })).toBeEnabled();
    });
  });

  it('says so when the test does not exist', async () => {
    server.use(http.get('/api/v1/tests/unknown', () => problem(404, 'Test not found', 'No test unknown')));

    renderApp('/tests/unknown');

    expect(await screen.findByRole('heading', { name: 'Test not found' })).toBeInTheDocument();
  });
});
